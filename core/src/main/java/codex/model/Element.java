//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.model;

/** Provides information on a source code element. */
public interface Element {

  /** The project id that defines this element or its referent. */
  int projectId ();

  /** The id of this element or it's referent. */
  int id ();

  /** The offset into the source text at which this element occurs. */
  int offset ();

  /** The length of this element, in characters. */
  int length ();

  /** The kind of this element. */
  Kind kind ();

  /** Returns true if this element and {@code other} refer to the same def. This could be two uses
    * of the same def, or a use and the def itself, etc. */
  default boolean sameReferent (Element other) {
    return projectId() == other.projectId() && id() == other.id();
  }
}
