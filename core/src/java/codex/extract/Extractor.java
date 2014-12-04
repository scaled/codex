//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.extract;

/**
 * Provides a basic interface for extraction. Extractors can provide other entry points, but this
 * allows basic abstraction over extractors for different languages.
 */
public interface Extractor {

  /** Processes {@code sources}. Metadata is emitted to {@code writer}. */
  void process (SourceSet sources, Writer writer) throws Exception;
}
