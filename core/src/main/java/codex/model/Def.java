//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.model;

/**
 * Represents the definition of a name somewhere in code.
 */
public final class Def implements Element {

  /** The id of the project in which this def originates. */
  public final int projectId;

  /** The unique (within the def's project) id of this def. */
  public final int id;

  /** The id of the def that encloses this def, or 0 if it unenclosed. This will always be a another
    * def in the same project. */
  public final int outerId;

  /** The kind of the def. */
  public final Kind kind;

  /** Whether or not this def is exported outside the scope of its enclosing element. This is not
    * used for analysis, but rather to efficiently filter defs during searches . */
  public final boolean exported;

  /** The name defined by this def. */
  public final String name;

  /** The character offset in the source text at which this def occurs. */
  public final int offset;

  public Def (int projectId, int id, int outerId, Kind kind, boolean exported,
              String name, int offset) {
    this.projectId = projectId;
    this.id = id;
    this.outerId = outerId;
    this.kind = kind;
    this.exported = exported;
    this.name = name;
    this.offset = offset;
  }

  /** Returns true if this def is structurally equal to {@code other}. */
  public boolean equals (Def other) {
    return (projectId == other.projectId && id == other.id && outerId == other.outerId &&
            kind == other.kind && exported == other.exported && name.equals(other.name) &&
            offset == other.offset);
  }

  @Override public Ref ref () { return Ref.local(projectId, id); }
  @Override public int offset () { return offset; }
  @Override public int length () { return name.length(); }
  @Override public Kind kind () { return kind; }

  @Override public int hashCode () {
    return projectId ^ id ^ outerId;
  }

  @Override public boolean equals (Object other) {
    return (other instanceof Def) && equals((Def)other);
  }

  @Override public String toString () {
    return String.format("Def(%d, %d, %d, %s, %s, %s, %d)",
                         projectId, id, outerId, kind, exported, name, offset);
  }
}
