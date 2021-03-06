//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.model;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Defines the different places from which source comes.
 */
public abstract class Source {

  /** Models a source file in the file system. */
  public static class File extends Source {

    /** The absolute path to the source file. */
    public final String path;

    public File (Path path) {
      this(path.toString());
    }

    public File (String path) {
      if (path == null) throw new NullPointerException();
      this.path = path;
    }

    @Override public long lastModified () throws IOException {
      return Files.getLastModifiedTime(Paths.get(path)).toMillis();
    }

    @Override public Reader reader () throws IOException {
      return new FileReader(path);
    }

    @Override public boolean equals (Object other) {
      return (other instanceof File) && path.equals(((File)other).path);
    }
    @Override public int hashCode () {
      return path.hashCode();
    }
    @Override public String toString () {
      return path;
    }

    @Override protected String path () {
      return path;
    }
    @Override protected char pathSeparator () {
      return java.io.File.separatorChar;
    }
  }

  /** Models a source file inside an archive file (zip, jar, etc.). */
  public static class ArchiveEntry extends Source {

    /** The absolute path to the archive file. */
    public final String archivePath;

    /** The path to the source file, inside the archive file. */
    public final String sourcePath;

    public ArchiveEntry (Path archivePath, String sourcePath) {
      this(archivePath.toString(), sourcePath);
    }

    public ArchiveEntry (String archivePath, String sourcePath) {
      if (archivePath == null || sourcePath == null) throw new NullPointerException();
      this.archivePath = archivePath;
      this.sourcePath = sourcePath;
    }

    @Override public long lastModified () throws IOException {
      return Files.getLastModifiedTime(Paths.get(archivePath)).toMillis();
    }

    @Override public Reader reader () throws IOException {
      ZipFile file = new ZipFile(archivePath);
      ZipEntry entry = file.getEntry(sourcePath);
      return new InputStreamReader(file.getInputStream(entry), "UTF-8");
    }

    @Override public boolean equals (Object other) {
      return ((other instanceof ArchiveEntry) &&
              archivePath.equals(((ArchiveEntry)other).archivePath) &&
              sourcePath.equals(((ArchiveEntry)other).sourcePath));
    }
    @Override public int hashCode () {
      return archivePath.hashCode() ^ sourcePath.hashCode();
    }
    @Override public String toString () {
      return archivePath + "!" + sourcePath;
    }

    @Override protected String path () {
      return sourcePath;
    }
    @Override protected char pathSeparator () {
      return '/'; // zip path separator always '/'
    }
  }

  /**
   * Creates a source from the supplied string representation. {@code string} should be the result
   * of calling {@link Source#toString} on an existing source.
   */
  public static Source fromString (String string) {
    int eidx = string.indexOf('!');
    if (eidx == -1) return new File(string);
    else {
      String archive = string.substring(0, eidx), entry = string.substring(eidx+1);
      // javac prepends its zip entry paths with a leading / but that's wrong wrong wrong
      if (entry.startsWith("/")) entry = entry.substring(1);
      return new ArchiveEntry(archive, entry);
    }
  }

  /** Returns the name of the file represented by this source. */
  public String fileName () {
    String path = path();
    return path.substring(path.lastIndexOf(pathSeparator())+1);
  }

  /** Returns the extension of the file represented by this source, or "" if it has none. */
  public String fileExt () {
    String path = path();
    int didx = path.lastIndexOf('.');
    return (didx == -1) ? "" : path.substring(didx+1);
  }

  /** Returns the path to this source with {@code root} stripped from it, if applicable. */
  public String relativePath (String root) {
    String path = path();
    if (path.startsWith(root)) {
      path = path.substring(root.length());
    }
    if (path.length() > 0 && path.charAt(0) == pathSeparator()) {
      path = path.substring(1);
    }
    return path;
  }

  /** Returns the last modified time of the source file. */
  public abstract long lastModified () throws IOException;

  /** Creates a reader that can be used to read the contents of this source. Note: this reader is
    * not buffered, wrap it in a {@link BufferedReader} as appropriate. */
  public abstract Reader reader () throws IOException;

  protected abstract String path ();
  protected abstract char pathSeparator ();

  private Source () {} // seal it!
}
