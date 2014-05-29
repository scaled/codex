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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;

/**
 * Handles extraction of Codex metadata from Java source code. This effectively compiles the code
 * using the standard Java compiler, but stops short of generating bytecode.
 */
public class JavaExtractor {

  /** Provides the classpath used by the compiler. */
  public Iterable<Path> classpath () { return Collections.emptyList(); }

  /** Processes {@code files}. Metadata is emitted to {@code writer}. */
  public void process (Iterable<Path> files, Writer writer) {
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

  private void process0 (Iterable<? extends JavaFileObject> files, Writer writer) {
    try {
      List<String> opts = Lists.newArrayList("-Xjcov");

      String cp = Joiner.on(File.pathSeparator).join(classpath());
      if (cp.length() > 0) opts.add("-classpath " + cp);

      JavacTaskImpl task = (JavacTaskImpl)_compiler.getTask(null, null, null, opts, null, files);
      Iterable<? extends CompilationUnitTree> asts = task.parse();
      task.analyze(); // don't need results, but need annotations in tree

      Context context = task.getContext();
      ExtractingScanner scanner = new ExtractingScanner(Types.instance(context));
      for (CompilationUnitTree tree : asts) scanner.extract(tree, writer);

      // annoyingly, there's no (public) way to tell the task that we're done without generating
      // .class files, so instead we have to do this reach around
      Method endContext = Iterables.find(Arrays.asList(task.getClass().getDeclaredMethods()),
                                         m -> m.getName().equals("cleanup"));
      endContext.setAccessible(true);
      endContext.invoke(task);

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

  private JavacTool _compiler = JavacTool.create();
}
