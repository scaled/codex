//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.extract;

import codex.model.*;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public abstract class AbstractExtractor implements Extractor {

  @Override public void process (SourceSet sources, Writer writer) throws IOException {
    writer.openSession();
    try {
      if (sources instanceof SourceSet.Files) {
        for (Path path : ((SourceSet.Files)sources).paths) {
          process(new Source.File(path.toString()), new FileReader(path.toFile()), writer);
        }
    } else {
        SourceSet.Archive sa = (SourceSet.Archive)sources;
        ZipFile zip = new ZipFile(sa.archive.toFile());
        String zipPath = sa.archive.toString();
        for (ZipEntry entry :
             zip.stream().filter(sa.filter).collect(Collectors.<ZipEntry>toList())) {
          process(new Source.ArchiveEntry(zipPath, entry.getName()),
                  new InputStreamReader(zip.getInputStream(entry), "UTF-8"), writer);
        }
      }
    } finally {
      writer.closeSession();
    }
  }

  /** Combines {@code file} and {@code code} into a test file and processes it.
    * Metadata is emitted to {@code writer}. */
  public void process (String file, String code, Writer writer) throws IOException {
    writer.openSession();
    try {
      process(new Source.File(file), new StringReader(code), writer);
    } finally {
      writer.closeSession();
    }
  }

  protected abstract void process (Source source, Reader reader, Writer writer) throws IOException;
}
