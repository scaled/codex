//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex;

import codex.model.*;
import codex.store.ProjectStore;
import com.carrotsearch.hppc.IntObjectMap;
import com.carrotsearch.hppc.IntObjectOpenHashMap;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The main entry point for code metadata. A Codex groups together a set of related {@link
 * ProjectStore}s (generally a leaf project and all of its dependencies) and handles resolution of
 * inter-project references.
 */
public class Codex {

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

  /** Encapsulates a search query. */
  public static class Query {
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

    public Query kind (Kind kind) {
      return new Query(EnumSet.of(kind), name, prefix, locality);
    }
    public Query kinds (Kind first, Kind... rest) {
      return new Query(EnumSet.of(first, rest), name, prefix, locality);
    }
    public Query expExportedOnly () {
      return new Query(kinds, name, prefix, Locality.EXPORTED_ONLY);
    }
    public Query expAll () {
      return new Query(kinds, name, prefix, Locality.ALL);
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

  /**
   * Creates a codex with the supplied set of stores. The stores should be in order of precedence:
   * the first store having the highest precedence, the last having the lowest. Global references
   * will be resolved by querying each store in turn, and the first to claim knowledge of a ref is
   * considered definitive.
   *
   * <p>This models the Java classpath mechanism, but the expectation is that all compilers use a
   * similar "canonical global ordering" that gives a consistent precedence ordering when resolving
   * names. Of course, this is only even meaningful when multiple projects claim knowledge of the
   * same global names, but you'd be surprised how often that happens in practice.</p>
   */
  public Codex (Iterable<ProjectStore> stores) {
    _stores = Lists.newArrayList(stores);
  }

  /**
   * Returns all stores known to this codex, from highest precedence to lowest.
   */
  public Iterable<ProjectStore> stores () {
    return _stores;
  }

  /**
   * Returns the store that contains index information for {@code source}, if available.
   */
  public Optional<ProjectStore> storeFor (Source source) {
    for (ProjectStore store : _stores) {
      if (store.isIndexed(source)) return Optional.of(store);
    }
    return Optional.empty();
  }

  /**
   * Resolves the {@link Def} for {@code ref}.
   */
  public Optional<Def> resolve (Ref ref) {
    if (ref instanceof Ref.Local) {
      Ref.Local lref = (Ref.Local)ref;
      return Optional.of(lref.project.def(lref.defId));

    } else {
      Ref.Global gref = (Ref.Global)ref;
      for (ProjectStore store : _stores) {
        Optional<Def> odef = store.def(gref);
        if (odef.isPresent()) return odef;
      }
      return Optional.empty();
    }
  }

  /**
   * Returns a global reference for {@code def}.
   * @throws NoSuchElementException if def did not originate from one of our project stores.
   */
  public Ref.Global ref (Def def) {
    return def.project.ref(def.id);
  }

  /**
   * Locates the store that handles {@code source} and calls {@link ProjectStore#visit} on it.
   * @return true if elems were delivered, false if no project knew of {@code source}.
   */
  public boolean visit (Source source, Consumer<Element> cons) {
    for (ProjectStore ps : _stores) if (ps.visit(source, cons)) return true;
    return false;
  }

  /**
   * Finds all defs matching the criteria established by {@code query}. Name comparison is done
   * case-insensitively.
   */
  public List<Def> find (Query query) {
    List<Def> matches = new ArrayList<>();
    // TODO: support filtering non-public from dependent stores, other query bits
    ProjectStore primary = _stores.get(0);
    for (ProjectStore store : _stores) store.find(
      query, query.locality.expOnly(store == primary), matches);
    return matches;
  }

  private final List<ProjectStore> _stores;
}
