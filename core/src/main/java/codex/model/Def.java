//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.model;

import codex.store.ProjectStore;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents the definition of a name somewhere in code.
 */
public final class Def implements Element {

  /** The project from which this def originates. */
  public final ProjectStore project;

  /** The unique (within the def's project) id of this def. Note: this is a boxed long simply to
    * avoid repeated boxing and unboxing. It will never be null. */
  public final Long id;

  /** The id of the def that encloses this def, or null if it unenclosed. This will always be a
    * another def in the same project. */
  public final Long outerId;

  /** The kind of the def. */
  public final Kind kind;

  /** The flavor of the def. */
  public final Flavor flavor;

  /** Whether or not this def is visible outside its compilation unit. */
  public final boolean exported;

  /** The access level of the def. */
  public final Access access;

  /** The name defined by this def. */
  public final String name;

  /** The character offset in the source text at which this def occurs. */
  public final int offset;

  /** The character offset at which this def's body starts. */
  public final int bodyStart;

  /** The character offset at which this def's body ends. */
  public final int bodyEnd;

  public Def (ProjectStore project, Long id, Long outerId, Kind kind, Flavor flavor,
              boolean exported, Access access, String name,
              int offset, int bodyStart, int bodyEnd) {
    assert project != null;
    this.project = project;
    this.id = id;
    this.outerId = outerId;
    this.kind = kind;
    this.flavor = flavor;
    this.exported = exported;
    this.access = access;
    this.name = name;
    this.offset = offset;
    this.bodyStart = bodyStart;
    this.bodyEnd = bodyEnd;
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

  /** Resolves and returns the members nested directly under this def. */
  public Iterable<Def> members () {
    return project.defsIn(id);
  }

  /** Resolves and returns this def's enclosing def. Returns null if it is not enclosed. */
  public Def outer () {
    return (outerId == null) ? null : project.def(outerId);
  }

  /** Returns a global reference to this def. */
  public Ref.Global globalRef () {
    Ref.Global ref = project.ref(id);
    assert ref != null : "No global ref for " + this;
    return ref;
  }

  /** Computes and returns the fully qualified name of this def. */
  public String fqName () {
    return toFqName(globalRef());
  }

  /** Computes and returns the qualifier for this def. This is {@link #fqName} minus the name of the
    * def and the path separator that joins it to its qualifier. */
  public String qualifier () {
    return toFqName(globalRef().parent);
  }

  /** Returns a more useful string representation of this def's {@link #id}. For to debug! */
  public String idToString () {
    return project.idToString(id);
  }

  /** Returns a more useful string representation of this def's {@link #outerId}. For to debug! */
  public String outerIdToString () {
    return (outerId == null) ? "<null>" : project.idToString(outerId);
  }

  /** Returns true if this def is structurally equal to {@code other}. */
  public boolean equals (Def od) {
    return (project == od.project && id.equals(od.id) &&
            Objects.equals(outerId, od.outerId) && kind == od.kind && flavor == od.flavor &&
            exported == od.exported && access == od.access && name.equals(od.name) &&
            offset == od.offset && bodyStart == od.bodyStart && bodyEnd == od.bodyEnd);
  }

  @Override public Ref ref () { return Ref.local(project, id); }
  @Override public int offset () { return offset; }
  @Override public int length () { return name.length(); }
  @Override public Kind kind () { return kind; }

  @Override public int hashCode () {
    return project.hashCode() ^ id.intValue();
  }

  @Override public boolean equals (Object other) {
    return (other instanceof Def) && equals((Def)other);
  }

  @Override public String toString () {
    return String.format("Def(%s, %s, %s, %s, %s, %s, %d)",
                         project, id, outerId, kind, exported, name, offset);
  }

  protected String toFqName (Ref.Global ref) {
    if (ref == Ref.Global.ROOT) return "";
    Lang lang = Lang.forExt(source().fileExt());
    StringBuilder sb = new StringBuilder();
    toFqName(ref, lang, sb);
    return sb.toString();
  }

  protected void toFqName (Ref.Global ref, Lang lang, StringBuilder sb) {
    if (ref.parent != Ref.Global.ROOT) {
      toFqName(ref.parent, lang, sb);
    }
    Kind kind = project.def(ref).map(Def::kind).orElse(Kind.MODULE);
    if (sb.length() > 0) sb.append(lang.pathPrefix(kind));
    sb.append(ref.id);
  }
}
