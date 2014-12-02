//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.store;

import codex.model.Def;
import codex.model.Kind;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Encapsulates a search query. */
public class Query {

  /** Controls whether non-exported defs are included in queries. */
  public static enum Locality {
    /** Return only exported defs. */
    EXPORTED_ONLY {
      boolean expOnly (boolean primaryStore) { return true; }
    },
    /** Return exported and non-exported defs from our primary store,
     * only exported defs from dependent stores. */
    EXPORTED_DEPENDENTS {
      boolean expOnly (boolean primaryStore) { return !primaryStore; }
    },
    /** Return exported and non-exported defs from all stores. */
    ALL {
      boolean expOnly (boolean primaryStore) { return false; }
    };

    abstract boolean expOnly (boolean primaryStore);
  }

  /** The set of kinds to consider. */
  public final Set<Kind> kinds;
  /** The name, or name prefix, to be matched. This is always lowercase. */
  public final String name;
  /** Whether {@link #name} is exact or a prefix. */
  public final boolean prefix;
  /** Indicates the criteria for including non-exported defs. */
  public final Locality locality;

  /** Returns a query that matches {@code name} completely. */
  public static Query name (String name) { return new Query(name, false); }
  /** Returns a query that matches {@code name} as a prefix. */
  public static Query prefix (String name) { return new Query(name, true); }

  /** Copies this query and sets {@link #kinds} to just {@code kind}. */
  public Query kind (Kind kind) {
    return kinds(EnumSet.of(kind));
  }
  /** Copies this query and sets {@link #kinds} to {@code kinds}. */
  public Query kinds (Set<Kind> kinds) {
    return new Query(kinds, name, prefix, locality);
  }
  /** Copies this query and configures it to return only exported defs. */
  public Query expExportedOnly () {
    return new Query(kinds, name, prefix, Locality.EXPORTED_ONLY);
  }
  /** Copies this query and configures it to return all defs. */
  public Query expAll () {
    return new Query(kinds, name, prefix, Locality.ALL);
  }

  /**
   * Issues this query to all project stores in {@code stores}. Returns all defs matching our
   * criteria. Name comparison is done case-insensitively.
   */
  public List<Def> find (Iterable<ProjectStore> stores) {
    // TODO: support filtering non-public from dependent stores, other query bits
    List<Def> matches = new ArrayList<>();
    boolean primary = true; // the first store is primary
    for (ProjectStore store : stores) {
      store.find(this, locality.expOnly(primary), matches);
      primary = false; // subsequent stores are not
    }
    return matches;
  }

  @Override public String toString () {
    return "Query(" + name + ", pre=" + prefix + ", loc=" + locality + ", kinds=" + kinds + ")";
  }

  private Query (Set<Kind> kinds, String name, boolean prefix, Locality locality) {
    this.kinds = kinds;
    this.name = name.toLowerCase();
    this.prefix = prefix;
    this.locality = locality;
  }

  private Query (String name, boolean prefix) {
    this(EnumSet.allOf(Kind.class), name, prefix, Locality.EXPORTED_DEPENDENTS);
  }
}
