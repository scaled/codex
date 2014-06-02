//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.store;

import codex.Codex;
import codex.extract.StoreWriter;
import codex.extract.Writer;
import codex.model.*;
import com.carrotsearch.hppc.IntObjectMap;
import com.carrotsearch.hppc.IntObjectOpenHashMap;
import com.carrotsearch.hppc.IntOpenHashSet;
import com.carrotsearch.hppc.IntSet;
import com.carrotsearch.hppc.cursors.IntCursor;
import com.google.common.collect.Iterables;
import com.google.common.collect.TreeMultimap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

/**
 * A completely in-memory store.
 */
public class EphemeralStore extends ProjectStore {

  /** A writer that can be used to write metadata to this store. Handles incremental updates. */
  public final Writer writer = new StoreWriter(projectId) {

    @Override public void closeUnit () {
      super.closeUnit();
      // purge any old def ids from this unit
      if (!_oldDefIds.isEmpty()) removeDefs(_oldDefIds);
      _curIdMap = null;
      _oldDefIds = null;
    }

    @Override protected int assignUnitId (Source source) {
      IdMap idMap = _unitIdMap.get(source);
      if (idMap != null) {
        _curIdMap = idMap;
        _oldDefIds = idMap.copyIds();
        return idMap.unitId;
      } else {
        int unitId = _unitSource.size()+1;
        _unitSource.put(unitId, source);
        _unitIdMap.put(source, _curIdMap = new IdMap(_projectRefs, unitId));
        _oldDefIds = new IntOpenHashSet();
        return unitId;
      }
    }

    @Override protected int resolveDefId (Ref.Global id) {
      return _curIdMap.resolve(id);
    }

    @Override protected void storeDef (Def def) {
      _defs.put(def.id, def);
      if (def.outerId != 0) {
        List<Def> members = _defMembers.get(def.outerId);
        if (members == null) _defMembers.put(def.outerId, members = new ArrayList<>());
        members.add(def);
      } else {
        _topDefs.put(def.id, def);
      }
      _indices.get(def.kind).put(def.name.toLowerCase(), def);
    }

    @Override protected void storeUse (int defId, Use use) {
      List<Use> uses = _defUses.get(defId);
      if (uses == null) _defUses.put(defId, uses = new ArrayList<>());
      uses.add(use);
    }

    @Override protected void storeSig (int defId, Sig sig) {
      _defSig.put(defId, sig);
    }

    @Override protected void storeDoc (int defId, Doc doc) {
      _defDoc.put(defId, doc);
    }

    private IdMap _curIdMap;
    private IntSet _oldDefIds;
  };

  public EphemeralStore (int projectId) {
    super(projectId);
  }

  /**
   * Wipes the contents of this store, preparing it to be rebuild from scratch.
   */
  public void clear () {
    _unitSource.clear();
    _unitIdMap.clear();
    _topDefs.clear();
    _defs.clear();
    _defMembers.clear();
    _defUses.clear();
    _defSig.clear();
    _defDoc.clear();
  }

  /**
   * Returns the number of defs in this store.
   */
  public int defCount () {
    return _projectRefs.size();
  }

  @Override public Iterable<Def> topLevelDefs () {
    return _topDefs.values();
  }

  @Override public boolean isIndexed (Source source) {
    return _unitIdMap.containsKey(source);
  }

  @Override public Iterable<Def> sourceDefs (Source source) {
    IdMap idMap = _unitIdMap.get(source);
    if (idMap == null) throw new IllegalArgumentException("Unknown source " + source);
    // TODO: copying the ids here is kinda expensive, come up with another approach? maybe not, any
    // other approach would likely result in materializing a list to hold the defs, which is pretty
    // much just as expensive...
    return Iterables.transform(idMap.copyIds(), ic -> def(ic.value));
  }

  @Override public Optional<Def> def (Ref.Global ref) {
    int id = _projectRefs.get(ref);
    // TODO: we should probably freak out if we have an id mapping but no def...
    return (id == 0) ? Optional.empty() : Optional.ofNullable(_defs.get(id));
  }

  @Override public Ref.Global ref (int defId) {
    return _projectRefs.get(defId);
  }

  @Override public Def def (int defId) {
    return reqdef(defId, _defs.get(defId));
  }

  @Override public List<Def> memberDefs (int defId) {
    List<Def> members = _defMembers.get(defId);
    return (members == null) ? Collections.emptyList() : members;
  }

  @Override public List<Use> uses (int defId) {
    List<Use> uses = _defUses.get(defId);
    return (uses == null) ? Collections.emptyList() : uses;
  }

  @Override public Optional<Sig> sig (int defId) {
    return Optional.ofNullable(_defSig.get(defId));
  }

  @Override public Optional<Doc> doc (int defId) {
    return Optional.ofNullable(_defDoc.get(defId));
  }

  @Override public Source source (int defId) {
    return reqdef(defId, _unitSource.get(IdMap.toUnitId(defId)));
  }

  @Override public void find (Codex.Query query, boolean expOnly, List<Def> into) {
    for (Kind kind : query.kinds) {
      TreeMultimap<String,Def> index = _indices.get(kind);
      // if we're doing an exact match, just look up name directly
      if (!query.prefix) add(index.get(query.name), expOnly, into);
      else {
        // if we're doing a prefix match, iterate over the index starting from the first key that's
        // >= name, and stop when we reach a key that no longer starts with our prefix
        String pre = query.name;
        for (Map.Entry<String,Collection<Def>> entry : index.asMap().tailMap(pre).entrySet()) {
          if (!entry.getKey().startsWith(pre)) break;
          add(entry.getValue(), expOnly, into);
        }
      }
    }
  }

  private void add (Collection<Def> defs, boolean expOnly, List<Def> into) {
    for (Def def : defs) if (!expOnly || def.exported) into.add(def);
  }

  @Override public void close () {} // noop!

  private void removeDefs (IntSet defIds) {
    _projectRefs.remove(defIds);
    for (IntCursor ic : defIds) _topDefs.remove(ic.value);
    _defs.removeAll(defIds);
    _defMembers.removeAll(defIds);
    _defUses.removeAll(defIds);
    _defSig.removeAll(defIds);
    _defDoc.removeAll(defIds);
  }

  private <T> T reqdef (int defId, T value) {
    if (value == null) throw new NoSuchElementException("No def with id " + defId);
    return value;
  }

  private final RefTree _projectRefs = new RefTree();
  private final IntObjectMap<Source> _unitSource = new IntObjectOpenHashMap<>();
  private final Map<Source,IdMap> _unitIdMap = new HashMap<>();
  private final Map<Integer,Def> _topDefs = new HashMap<>();
  private final IntObjectMap<Def> _defs = new IntObjectOpenHashMap<>();
  private final IntObjectMap<List<Def>> _defMembers = new IntObjectOpenHashMap<>();
  private final IntObjectMap<List<Use>> _defUses = new IntObjectOpenHashMap<>();
  private final IntObjectMap<Sig> _defSig = new IntObjectOpenHashMap<>();
  private final IntObjectMap<Doc> _defDoc = new IntObjectOpenHashMap<>();
  private final Map<Kind,TreeMultimap<String,Def>> _indices = new HashMap<>(); {
    for (Kind kind : Kind.values()) _indices.put(kind, TreeMultimap.create());
  }
}
