//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.model;

/**
 * Represents the use of a name somewhere in code.
 */
public class Use {

  /** The id of the project that contains the referent. */
  public final int refProjectId;

  /** The id of the referent. */
  public final int refId;

  /** The kind of the referent. */
  public final Kind refKind;

  /** The character offset in the source text at which this use occurs. */
  public final int offset;

  /** The length (in characters) of the use string. */
  public final int length;

  // TODO: track kind of use? read, write, invoke? are there others?

  public Use (int refProjectId, int refId, Kind refKind, int offset, int length) {
    this.refProjectId = refProjectId;
    this.refId = refId;
    this.refKind = refKind;
    this.offset = offset;
    this.length = length;
  }

  @Override public int hashCode () {
    return refProjectId ^ refId ^ offset ^ length;
  }

  @Override public boolean equals (Object other) {
    if (!(other instanceof Use)) return false;
    Use o = (Use)other;
    return (refProjectId == o.refProjectId && refId == o.refId && refKind == o.refKind &&
            offset == o.offset && length == o.length);
  }
}
