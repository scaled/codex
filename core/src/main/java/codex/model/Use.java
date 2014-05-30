//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.model;

/**
 * Represents the use of a name somewhere in code.
 */
public final class Use {

  /** The id of the referent. */
  public final Id refId;

  /** The kind of the referent. */
  public final Kind refKind;

  /** The character offset in the source text at which this use occurs. */
  public final int offset;

  /** The length (in characters) of the use string. */
  public final int length;

  // TODO: track kind of use? read, write, invoke? are there others?

  public Use (Id refId, Kind refKind, int offset, int length) {
    this.refId = refId;
    this.refKind = refKind;
    this.offset = offset;
    this.length = length;
  }

  /** Returns true if this use is structually equal to {@code other}. */
  public boolean equals (Use other) {
    return (refId.equals(other.refId) && refKind == other.refKind &&
            offset == other.offset && length == other.length);
  }

  @Override public int hashCode () {
    return refId.hashCode() ^ offset ^ length;
  }

  @Override public boolean equals (Object other) {
    return (other instanceof Use) && equals((Use)other);
  }
}
