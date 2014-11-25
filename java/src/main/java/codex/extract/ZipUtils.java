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
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.tools.JavaFileObject;

public class ZipUtils {

  public static List<JavaFileObject> zipFiles (JavacTool javac, ZipFile file,
                                               Predicate<ZipEntry> filter) throws IOException {
    List<JavaFileObject> files = new ArrayList<>();
    JavacFileManager fm = javac.getStandardFileManager(null, null, null);
    ZipArchive arch = new ZipArchive(fm, file);
    for (ZipEntry entry : file.stream().collect(Collectors.<ZipEntry>toList())) {
      String name = entry.getName();
      name = name.substring(name.lastIndexOf("/")+1);
      if (name.endsWith(".java") && filter.test(entry)) {
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
  private static Constructor<?> zfoCtor = ZipArchive.ZipFileObject.class.getDeclaredConstructors()[0];
  static  { zfoCtor.setAccessible(true); }
}
