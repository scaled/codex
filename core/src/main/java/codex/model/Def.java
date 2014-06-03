//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.model;

import codex.store.ProjectStore;
import java.util.Optional;

/**
 * Represents the definition of a name somewhere in code.
 */
public final class Def implements Element {

  /** The project from which this def originates. */
  public final ProjectStore project;

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

  public Def (ProjectStore project, int id, int outerId, Kind kind, boolean exported,
              String name, int offset) {
    this.project = project;
    this.id = id;
    this.outerId = outerId;
    this.kind = kind;
    this.exported = exported;
    this.name = name;
    this.offset = offset;
  }

  /** Resolves and returns the source file in which this def occurs. */
  public Source source () {
    return project.source(id);
  }

  /** Resolves and returns the signature of the def, and sig defs/uses, if available. */
  public Optional<Sig> sig () {
    return project.sig(id);
  }

  /** Resolves and returns the documentation for the def, if available. */
  public Optional<Doc> doc () {
    return project.doc(id);
  }

  /** Returns true if this def is structurally equal to {@code other}. */
  public boolean equals (Def other) {
    return (project == other.project && id == other.id && outerId == other.outerId &&
            kind == other.kind && exported == other.exported && name.equals(other.name) &&
            offset == other.offset);
  }

  @Override public Ref ref () { return Ref.local(project, id); }
  @Override public int offset () { return offset; }
  @Override public int length () { return name.length(); }
  @Override public Kind kind () { return kind; }

  @Override public int hashCode () {
    return project.hashCode() ^ id ^ outerId;
  }

  @Override public boolean equals (Object other) {
    return (other instanceof Def) && equals((Def)other);
  }

  @Override public String toString () {
    return String.format("Def(%s, %d, %d, %s, %s, %s, %d)",
                         project, id, outerId, kind, exported, name, offset);
  }
}
