//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.model;

/**
 * Defines the different places from which source comes.
 */
public abstract class Source {

  /** Models a source file in the file system. */
  public static class File extends Source {

    /** The absolute path to the source file. */
    public final String path;

    public File (String path) {
      this.path = path;
    }

    @Override public boolean equals (Object other) {
      return (other instanceof File) && path.equals(((File)other).path);
    }

    @Override public int hashCode () {
      return path.hashCode();
    }
  }

  /** Models a source file inside an archive file (zip, jar, etc.). */
  public static class ArchiveEntry extends Source {

    /** The absolute path to the archive file. */
    public final String archivePath;

    /** The path to the source file, inside the archive file. */
    public final String sourcePath;

    public ArchiveEntry (String archivePath, String sourcePath) {
      this.archivePath = archivePath;
      this.sourcePath = sourcePath;
    }

    @Override public boolean equals (Object other) {
      return ((other instanceof ArchiveEntry) &&
              archivePath.equals(((ArchiveEntry)other).archivePath) &&
              sourcePath.equals(((ArchiveEntry)other).sourcePath));
    }

    @Override public int hashCode () {
      return archivePath.hashCode() ^ sourcePath.hashCode();
    }
  }

  private Source () {} // seal it!
}
