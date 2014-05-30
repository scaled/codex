//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.model;

/**
 * Uniquely identifies a def. This comes in two flavors: a local id, used within a project to
 * reference other defs in that same project, and a global id, used to reference defs in other
 * projects.
 */
public abstract class Id {

  /** Represents a def within the same project. */
  public static final class Local extends Id {
    public final int id;

    @Override public int hashCode () {
      return id;
    }

    @Override public boolean equals (Object other) {
      return (other instanceof Local) && id == ((Local)other).id;
    }

    @Override public String toString () {
      return "l" + id;
    }

    private Local (int id) {
      this.id = id;
    }
  }

  /** Represents a def in any project. */
  public static final class Global extends Id {

    /** The parent component of this id. */
    public final Global parent;

    /** The simple identifier for this component of the id. An interned string. */
    public final String id;

    /** Returns a global id with {@code this} as its parent and {@code id} as its leaf. */
    public Global plus (String id) {
      return new Global(this, id);
    }

    @Override public int hashCode () {
      return id.hashCode() ^ (parent == ROOT ? 13 : parent.hashCode());
    }

    @Override public boolean equals (Object other) {
      return (other instanceof Global) && equals((Global)other);
    }

    @Override public String toString () {
      StringBuilder sb = new StringBuilder();
      toString(sb);
      return sb.toString();
    }

    private void toString (StringBuilder sb) {
      if (parent != ROOT) {
        sb.append(" ");
        parent.toString(sb);
      }
      sb.append(id);
    }

    private boolean equals (Global other) {
      // TODO: intern whole global instances and compare by reference?
      return id == other.id && (parent == other.parent || parent.equals(other.parent));
    }

    private Global (Global parent, String id) {
      this.parent = parent;
      this.id = id.intern();
    }
  }

  /** The root global name. */
  public static final Global ROOT = new Global(null, null);

  /** Returns a local id for {@code id}. */
  public static Id local (int id) {
    return new Local(id);
  }

  private Id () {} // prevent other subclasses
}
