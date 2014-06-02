//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.model;

/**
 * Represents the use of a name somewhere in code.
 */
public final class Use implements Element {

  /** Identifies the referent. This may be a local or global ref. */
  public final Ref ref;

  /** The kind of the referent. */
  public final Kind refKind;

  /** The character offset in the source text at which this use occurs. */
  public final int offset;

  /** The length (in characters) of the use string. */
  public final int length;

  // TODO: track kind of use: read, write, invoke, etc.?

  public Use (Ref ref, Kind refKind, int offset, int length) {
    this.ref = ref;
    this.refKind = refKind;
    this.offset = offset;
    this.length = length;
  }

  /** Returns true if this use is structually equal to {@code other}. */
  public boolean equals (Use other) {
    return (ref.equals(other.ref) && refKind == other.refKind &&
            offset == other.offset && length == other.length);
  }

  @Override public Ref ref () { return ref; }
  @Override public int offset () { return offset; }
  @Override public int length () { return length; }
  @Override public Kind kind () { return refKind; }

  @Override public int hashCode () {
    return ref.hashCode() ^ refKind.hashCode() ^ offset ^ length;
  }

  @Override public boolean equals (Object other) {
    return (other instanceof Use) && equals((Use)other);
  }
}
