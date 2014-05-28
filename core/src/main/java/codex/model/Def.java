//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.model;

/**
 * Represents the definition of a name somewhere in code.
 */
public class Def implements Element {

  /** The id of the project that contains this def. */
  public final int projectId;

  /** A unique (within this def's project) identifier for this def. */
  public final int id;

  /** The id of the def that encloses this def, or zero if it unenclosed. */
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

  @Override public int projectId () { return projectId; }
  @Override public int id () { return id; }
  @Override public int offset () { return offset; }
  @Override public int length () { return name.length(); }
  @Override public Kind kind () { return kind; }

  @Override public int hashCode () {
    return projectId ^ id;
  }

  @Override public boolean equals (Object other) {
    if (!(other instanceof Def)) return false;
    Def o = (Def)other;
    return (projectId == o.projectId && id == o.id && outerId == o.outerId && kind == o.kind &&
            exported == o.exported && name.equals(o.name) && offset == o.offset);
  }
}
