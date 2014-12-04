//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.extract;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import codex.model.Source;

/**
 * Encapsulates a set of sources which are to be processed by an extractor.
 */
public abstract class SourceSet {

  /** A simple list of paths to process. */
  public static class Files extends SourceSet {
    public final Iterable<Path> paths;
    public final int size;

    public Files (Iterable<Path> paths, int size) {
      this.paths = paths;
      this.size = size;
    }

    @Override public int size () { return size; }
  }

  /** A jar or zip archive of source files to process, potentially with a filter. */
  public static class Archive extends SourceSet {
    public final Path archive;
    public final Predicate<ZipEntry> filter;

    public Archive (Path archive) {
      this(archive, e -> true);
    }

    public Archive (Path archive, Predicate<ZipEntry> filter) {
      this.archive = archive;
      this.filter = filter;
    }

    @Override public int size () {
      try {
        ZipFile zf = new ZipFile(archive.toFile());
        int size = (int)zf.stream().filter(filter).count();
        zf.close();
        return size;
      } catch (Exception e) { return 0; }
    }
  }

  public static SourceSet create (Iterable<Path> paths, int size) {
    return new Files(paths, size);
  }

  public static SourceSet create (Path path) {
    return new Files(Collections.singletonList(path), 1);
  }

  public static SourceSet create (Source source) {
    if (source instanceof Source.ArchiveEntry) {
      Source.ArchiveEntry as = (Source.ArchiveEntry)source;
      return new Archive(Paths.get(as.archivePath), e -> e.getName().equals(as.sourcePath));
    } else {
      return create(Paths.get(((Source.File)source).path));
    }
  }

  /** Returns the number of files in this source set. */
  public abstract int size ();
}
