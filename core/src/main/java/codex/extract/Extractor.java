//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.extract;

import java.nio.file.Path;
import java.util.Collections;

/**
 * Provides a basic interface for extraction. Extractors can provide other entry points, but this
 * allows basic abstraction over extractors for different languages.
 */
public interface Extractor {

  /** Processes {@code files}. Metadata is emitted to {@code writer}. */
  void process (Iterable<Path> files, Writer writer);

  /** Processes {@code file}. Metadata is emitted to {@code writer}. */
  default void process (Path file, Writer writer) {
    process(Collections.singletonList(file), writer);
  }
}
