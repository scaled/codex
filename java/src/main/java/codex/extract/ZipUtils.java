//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.extract;

import com.sun.tools.javac.api.JavacTool;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.file.ZipArchive;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.tools.JavaFileObject;

// import com.google.common.io.CharStreams;
// import java.io.IOException;
// import java.io.InputStream;
// import java.io.InputStreamReader;
// import java.io.OutputStream;
// import java.io.Reader;
// import java.io.Writer;
// import java.net.URI;
// import java.util.stream.Collectors;
// import java.util.zip.ZipFile;
// import javax.lang.model.element.Modifier;
// import javax.lang.model.element.NestingKind;
// import javax.tools.JavaFileObject;

public class ZipUtils {

  public static List<JavaFileObject> zipFiles (JavacTool javac, ZipFile file) throws IOException {
    List<JavaFileObject> files = new ArrayList<>();
    JavacFileManager fm = (JavacFileManager)javac.getStandardFileManager(null, null, null);
    ZipArchive arch = new ZipArchive(fm, file);
    for (ZipEntry entry : file.stream().collect(Collectors.<ZipEntry>toList())) {
      String name = entry.getName();
      name = name.substring(name.lastIndexOf("/")+1);
      if (name.endsWith(".java")) {
        try {
          files.add((ZipArchive.ZipFileObject)zfoCtor.newInstance(arch, name, entry));
        } catch (Throwable t) {
          t.printStackTrace(System.err);
        }
      }
    }
    return files;
  }

  // the ZipFileObject constructor has protected access, but if we extend it then javac sees that
  // our file object is declared outside com.sun.tools.javac and wraps it in such a way that causes
  // it to choke when javac calls JavaFileManager.isSameFile (which it does when it encounters a
  // package-info.java file); yay for a twisty maze of bullshit
  private static Constructor zfoCtor = ZipArchive.ZipFileObject.class.getDeclaredConstructors()[0];
  static  { zfoCtor.setAccessible(true); }
  // public static Iterable<JavaFileObject> fileObjects (ZipFile file) {
  //   return file.stream().map(e -> new JavaFileObject() {
  //     public JavaFileObject.Kind getKind () { return JavaFileObject.Kind.SOURCE; }
  //     public boolean isNameCompatible (String simpleName, JavaFileObject.Kind kind) {
  //       return kind == JavaFileObject.Kind.SOURCE && simpleName.equals(e.getName());
  //     }
  //     public NestingKind getNestingKind () { return NestingKind.TOP_LEVEL; }
  //     public Modifier getAccessLevel () { return Modifier.PUBLIC; }

  //     public URI toUri () { return URI.create("file://" + file.getName() + "#" + e.getName()); }
  //     public String getName () { return e.getName(); }
  //     public long getLastModified () { return e.getTime(); }

  //     public InputStream openInputStream () throws IOException {
  //       System.err.println("Streaming " + getName());
  //       return file.getInputStream(e);
  //     }
  //     public Reader openReader (boolean ignoreEncodingErrors) throws IOException {
  //       return new InputStreamReader(openInputStream());
  //     }
  //     public CharSequence getCharContent (boolean ignoreEncodingErrors) throws IOException {
  //       Reader r = openReader(true);
  //       try {
  //         return CharStreams.toString(r);
  //       } finally {
  //         r.close();
  //       }
  //     }

  //     public OutputStream openOutputStream () { throw new UnsupportedOperationException(); }
  //     public Writer openWriter () { throw new UnsupportedOperationException(); }
  //     public boolean delete() { throw new UnsupportedOperationException(); }
  //   }).collect(Collectors.toList());
  // }
}
