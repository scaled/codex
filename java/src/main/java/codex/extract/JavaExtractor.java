//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.extract;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.api.JavacTool;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.util.Context;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;

/**
 * Handles extraction of Codex metadata from Java source code. This effectively compiles the code
 * using the standard Java compiler, but stops short of generating bytecode.
 */
public class JavaExtractor implements Extractor {

  public JavaExtractor () {
    this(JavacTool.create());
  }

  public JavaExtractor (JavacTool compiler) {
    _compiler = compiler;
  }

  /** Configures this extractor in summary mode. This causes it to omit the bodies of methods and
    * blocks when indexing. This is useful when indxing the JDK, where the shape of each class is
    * useful, but the actual implementations, not so much.
    */
  public JavaExtractor summaryMode () {
    _omitBodies = true;
    return this;
  }

  /** Provides the classpath used by the compiler. */
  public Iterable<Path> classpath () { return Collections.emptyList(); }

  @Override public void process (Iterable<Path> files, Writer writer) {
    StandardJavaFileManager fm = _compiler.getStandardFileManager(null, null, null); // TODO: args?
    process0(fm.getJavaFileObjectsFromFiles(Iterables.transform(files, Path::toFile)), writer);
  }

  /** Combines {@code file} and {@code code} into a test file and processes it.
    * Metadata is emitted to {@code writer}. */
  public void process (String file, String code, Writer writer) {
    process(Collections.singletonList(file), Collections.singletonList(code), writer);
  }

  /** Combines {@code files} and {@code codes} into test files and processes them.
    * Metadata is emitted to {@code writer}. */
  public void process (Iterable<String> files, Iterable<String> codes, Writer writer) {
    List<JavaFileObject> objs = Lists.newArrayList();
    Iterator<String> citer = codes.iterator();
    for (String file : files) objs.add(mkTestObject(file, citer.next()));
    process0(objs, writer);
  }

  /** Processes all .java source files in {@code files}. Metadata is emitted to {@code writer}. */
  public void process (ZipFile file, Writer writer) throws IOException {
    process(file, e -> true, writer);
  }

  /** Processes all .java source files in {@code files}. Metadata is emitted to {@code writer}.
    * @param filter a filter used to omit some entries from {@code file}.
    */
  public void process (ZipFile file, Predicate<ZipEntry> filter, Writer writer) throws IOException {
    process0(ZipUtils.zipFiles(_compiler, file, filter), writer);
  }

  /** Enables the filtering of certain compunits from the extraction process. All compunits passed
    * to {@code process} will be used to parse and analyze the code, but only compunits for which
    * this method returns true will be extracted. The default is to extract all compunits.
    *
    * This mainly exists for processing the JDK sources, where there are a zillion files, which we
    * want to include in the compilation process, but we only want to extract metadata for the
    * public java.* and javax.* APIs.
    */
  protected boolean filter (JavaFileObject compunit) {
    return true;
  }

  private void process0 (Iterable<? extends JavaFileObject> files, Writer writer) {
    try {
      List<String> opts = Lists.newArrayList("-Xjcov");

      String cp = Joiner.on(File.pathSeparator).join(classpath());
      if (cp.length() > 0) {
        opts.add("-classpath");
        opts.add(cp);
      }

      int[] diags = new int[Diagnostic.Kind.values().length];
      DiagnosticListener<JavaFileObject> diag = new DiagnosticListener<JavaFileObject>() {
        public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
          diags[diagnostic.getKind().ordinal()]++;
        }
      };
      JavacTaskImpl task = (JavacTaskImpl)_compiler.getTask(null, null, diag, opts, null, files);
      Iterable<? extends CompilationUnitTree> asts = task.parse();
      task.analyze(); // don't need results, but need annotations in tree

      writer.openSession();
      try {
        Context context = task.getContext();
        ExtractingScanner scanner = new ExtractingScanner(Types.instance(context), _omitBodies);
        for (CompilationUnitTree tree : asts) {
          if (filter(tree.getSourceFile())) scanner.extract(tree, writer);
        }
      } finally {
        writer.closeSession();
      }

      // annoyingly, there's no (public) way to tell the task that we're done without generating
      // .class files, so instead we have to do this reach around
      Method endContext = Iterables.find(Arrays.asList(task.getClass().getDeclaredMethods()),
                                         m -> m.getName().equals("cleanup"));
      endContext.setAccessible(true);
      endContext.invoke(task);

      // report the number of diagnostics
      StringBuilder sb = new StringBuilder();
      for (Diagnostic.Kind kind : Diagnostic.Kind.values()) {
        int count = diags[kind.ordinal()];
        if (count == 0) continue;
        if (sb.length() > 0) sb.append(", ");
        sb.append(kind.toString().toLowerCase()).append('=').append(count);
      }
      if (sb.length() > 0) System.err.println("Diagnostics [" + sb + "]");

    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private JavaFileObject mkTestObject (String file, String code) {
    return new SimpleJavaFileObject(URI.create("test:/" + file), JavaFileObject.Kind.SOURCE) {
      @Override public CharSequence getCharContent (boolean ignoreEncodingErrors) {
        return code;
      }
    };
  }

  private final JavacTool _compiler;
  private boolean _omitBodies;
}
