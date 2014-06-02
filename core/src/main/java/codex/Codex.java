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
    /** The name, or name prefix, to be matched. */
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
      this.name = name;
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
    for (ProjectStore store : stores) _byId.put(store.projectId, store);
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
   * Resolves and returns detail information for {@code ref}.
   */
  public Optional<DefInfo> resolve (Ref ref) {
    if (ref instanceof Ref.Local) {
      Ref.Local lref = (Ref.Local)ref;
      ProjectStore store = _byId.get(lref.projectId);
      if (store == null) throw new NoSuchElementException("Unknown project for local ref " + ref);
      return Optional.of(resolve(store, store.def(lref.defId)));

    } else {
      Ref.Global gref = (Ref.Global)ref;
      for (ProjectStore store : _stores) {
        Optional<Def> odef = store.def(gref);
        if (odef.isPresent()) return Optional.of(resolve(store, odef.get()));
      }
      return Optional.empty();
    }
  }

  /**
   * Delivers all known defs and uses in {@code source} to {@code cons}. The order in which the defs
   * and uses is unspecified, other than that each def will be immediately followed by the uses
   * nested immediately inside that def.
   *
   * <p>This is generally used to build an in-memory index for a given source file for things like
   * code highlighting and name resolution.</p>
   *
   * @return true if elems were delivered, false if no project knew of {@code source}.
   */
  public boolean index (Source source, Consumer<Element> cons) {
    Optional<ProjectStore> ostore = storeFor(source);
    ostore.ifPresent(store -> {
      for (Def def : store.sourceDefs(source)) {
        cons.accept(def);
        for (Use use : store.uses(def.id)) cons.accept(use);
      }
    });
    return ostore.isPresent();
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

  protected DefInfo resolve (ProjectStore store, Def def) {
    return new DefInfo(def, store.source(def.id), store.sig(def.id), store.doc(def.id));
  }

  private final List<ProjectStore> _stores;
  private final IntObjectMap<ProjectStore> _byId = new IntObjectOpenHashMap<>();
}
