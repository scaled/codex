//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.model;

/** Provides information on a source code element. */
public interface Element extends Comparable<Element> {

  /** A reference to this element or its referent. */
  Ref ref ();

  /** The offset into the source text at which this element occurs. */
  int offset ();

  /** The length of this element, in characters. */
  int length ();

  /** The kind of this element. */
  Kind kind ();

  /** Returns true if this element and {@code other} refer to the same def. This could be two uses
    * of the same def, or a use and the def itself, etc. */
  default boolean sameRef (Element other) {
    return ref().equals(other.ref());
  }

  default int compareTo (Element other) {
    // these are character offsets in source files and thus will never be so large as to overflow
    return offset() - other.offset();
  }
}
