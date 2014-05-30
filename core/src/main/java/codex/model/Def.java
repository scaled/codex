//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.model;

/**
 * Represents the definition of a name somewhere in code.
 */
public final class Def implements Element {

  /** The unique id of this def. */
  public final Id id;

  /** The id of the def that encloses this def, or null if it unenclosed. */
  public final Id outerId;

  /** The kind of the def. */
  public final Kind kind;

  /** Whether or not this def is exported outside the scope of its enclosing element. This is not
    * used for analysis, but rather to efficiently filter defs during searches . */
  public final boolean exported;

  /** The name defined by this def. */
  public final String name;

  /** The character offset in the source text at which this def occurs. */
  public final int offset;

  public Def (Id id, Id outerId, Kind kind, boolean exported, String name, int offset) {
    this.id = id;
    this.outerId = outerId;
    this.kind = kind;
    this.exported = exported;
    this.name = name;
    this.offset = offset;
  }

  /** Returns true if this def is structurally equal to {@code other}. */
  public boolean equals (Def other) {
    return (id.equals(other.id) && outerId.equals(other.outerId) && kind == other.kind &&
            exported == other.exported && name.equals(other.name) && offset == other.offset);
  }

  @Override public Id id () { return id; }
  @Override public int offset () { return offset; }
  @Override public int length () { return name.length(); }
  @Override public Kind kind () { return kind; }

  @Override public int hashCode () {
    return id.hashCode();
  }

  @Override public boolean equals (Object other) {
    return (other instanceof Def) && equals((Def)other);
  }
}
