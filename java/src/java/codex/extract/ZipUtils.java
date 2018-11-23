//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.extract;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javac.tools.javac.api.JavacTool;
import javac.tools.javac.file.*;
import javax.tools.JavaFileObject;

public class ZipUtils {

  public static Multiset<String> summarizeSources (ZipFile file) {
    Multiset<String> suffs = HashMultiset.create();
    file.stream().forEach(e -> { if (!e.isDirectory()) suffs.add(suffix(e.getName())); });
    return suffs;
  }

  private static String suffix (String name) {
    return name.substring(name.lastIndexOf('.')+1);
  }

  public static Predicate<ZipEntry> ofSuff (String suff) {
    return (ent -> ent.getName().endsWith(suff));
  }

  public static List<JavaFileObject> zipFiles (JavacTool javac, Path archive,
                                               Predicate<ZipEntry> filter) throws IOException {
    List<JavaFileObject> files = new ArrayList<>();
    ZipFile file = new ZipFile(archive.toFile());
    JavacFileManager fm = javac.getStandardFileManager(null, null, null);
    for (ZipEntry entry : file.stream().collect(Collectors.<ZipEntry>toList())) {
      if (entry.getName().endsWith(".java") && filter.test(entry)) {
        files.add(new ZipFileObject(fm, archive, file, entry));
      }
    }
    return files;
  }

  private static class ZipFileObject extends PathFileObject {
    private final ZipFile zip;
    private final ZipEntry entry;

    private ZipFileObject (BaseFileManager fileManager, Path path, ZipFile zip, ZipEntry entry) {
      super(fileManager, path);
      this.zip = zip;
      this.entry = entry;
    }

    @Override public String getName () {
      // The use of ( ) to delimit the entry name is not ideal
      // but it does match earlier behavior
      return entry.getName() + "(" + path + ")";
    }

    @Override public Kind getKind () {
      return BaseFileManager.getKind(entry.getName());
    }

    @Override public String inferBinaryName (Iterable<? extends Path> paths) {
      Path root = path.getFileSystem().getRootDirectories().iterator().next();
      return toBinaryName(root.relativize(path));
    }

    @Override public URI toUri () {
      // Work around bug JDK-8134451:
      // path.toUri() returns double-encoded URIs, that cannot be opened by URLConnection
      return createJarUri(path, entry.getName());
    }

    @Override public InputStream openInputStream () throws IOException {
      // fileManager.updateLastUsedTime();
      return zip.getInputStream(entry);
    }

    @Override
    public String toString () {
      return "ZipFileObject[" + entry.getName() + ":" + path + "]";
    }

    @Override
    public boolean equals(Object other) {
      return super.equals(other) && (other instanceof ZipFileObject) &&
        ((ZipFileObject)other).entry.equals(entry);
    }

    @Override
    public int hashCode() {
      return super.hashCode() ^ entry.hashCode();
    }

    @Override public PathFileObject getSibling (String baseName) {
      // return new ZipFileObject(fileManager, path.resolveSibling(baseName), entry.getName());
      throw new UnsupportedOperationException("getSibling(" + baseName + ")");
    }

    private static URI createJarUri (Path jarFile, String entryName) {
      URI jarURI = jarFile.toUri().normalize();
      String separator = entryName.startsWith("/") ? "!" : "!/";
      try {
        // The jar URI convention appears to be not to re-encode the jarURI
        return new URI("jar:" + jarURI + separator + entryName);
      } catch (URISyntaxException e) {
        throw new CannotCreateUriError(jarURI + separator + entryName, e);
      }
    }
  }
}
