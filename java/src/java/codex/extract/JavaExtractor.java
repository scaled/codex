//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.extract;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import javac.source.tree.CompilationUnitTree;
import javac.source.util.JavacTask;
import javac.tools.javac.api.JavacTaskImpl;
import javac.tools.javac.api.JavacTool;
import javac.tools.javac.code.Types;
import javac.tools.javac.util.Context;
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
  public JavaExtractor setSummaryMode (boolean summaryMode) {
    _omitBodies = summaryMode;
    return this;
  }

  /** Provides the classpath used by the compiler. */
  public Iterable<Path> classpath () { return Collections.emptyList(); }

  @Override public void process (SourceSet sources, Writer writer) throws IOException {
    if (sources instanceof SourceSet.Files) {
      Iterable<Path> files = ((SourceSet.Files)sources).paths;
      StandardJavaFileManager fm = _compiler.getStandardFileManager(null, null, null); // TODO: args?
      process0(fm.getJavaFileObjectsFromFiles(Iterables.transform(files, Path::toFile)), writer);
    } else {
      SourceSet.Archive sa = (SourceSet.Archive)sources;
      process0(ZipUtils.zipFiles(_compiler, sa.archive, sa.filter), writer);
    }
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

  protected void log (String message) {
    System.out.println(message);
  }

  private void process0 (Iterable<? extends JavaFileObject> files, Writer writer) {
    try {
      // we set our output dir to tmp.dir just in case annotation processors decide to generate
      // output even though we don't want any
      List<String> opts = Lists.newArrayList("-Xjcov", "-d", System.getProperty("java.io.tmpdir"));

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
          scanner.extract(tree, writer);
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
      if (sb.length() > 0) log("Diagnostics [" + sb + "]");

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
