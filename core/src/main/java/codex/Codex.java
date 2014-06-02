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
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The main entry point for code metadata. A Codex groups together a set of related {@link
 * ProjectStore}s (generally a leaf project and all of its dependencies) and handles resolution of
 * inter-project references.
 */
public class Codex {

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

  // TODO
  // /** Finds all defs matching the following criteria:
  //   *  - kind is in `kinds`
  //   *  - name equals `name` (if `prefix` is false) or
  //   *  - name starts with `name` (if `prefix` is true)
  //   * Name comparison is done case-insensitively. */
  // def find (kinds :Set[Kind], name :String, prefix :Boolean) :Seq[Def]

  protected DefInfo resolve (ProjectStore store, Def def) {
    return new DefInfo(def, store.source(def.id), store.sig(def.id), store.doc(def.id));
  }

  private final List<ProjectStore> _stores;
  private final IntObjectMap<ProjectStore> _byId = new IntObjectOpenHashMap<>();
}
