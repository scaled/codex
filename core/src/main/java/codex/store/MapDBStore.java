//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.store;

import codex.extract.BatchWriter;
import codex.extract.Writer;
import codex.model.*;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.mapdb.BTreeKeySerializer;
import org.mapdb.BTreeMap;
import org.mapdb.Bind;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Fun;
import org.mapdb.Serializer;

public class MapDBStore extends ProjectStore {

  // def ids are of the form UUDD where UU is the unit id and DD is the def id (exported defs have
  // DD between 1 and MAX_EXP_ID and non-exported defs have DD between MAX_EXP_ID+1 and MAX_DEF_ID)
  private static final int MAX_EXP_ID = 16384, MAX_DEF_ID = 65536;

  private static long toDefId (long unitId, long rawDefId) {
    return unitId * MAX_DEF_ID + rawDefId;
  }
  private static long toUnitId (long defId) {
    return defId / MAX_DEF_ID;
  }

  /** A writer that can be used to write metadata to this store. Handles incremental updates. */
  public final Writer writer = new BatchWriter() {

    // use this to commit every 100 compilation units; keeps WAL from getting too big
    private int _writeCount;
    private static final int COMMIT_EVERY = 100;

    private Map<Long,IdSet.Builder> _localRefs = new HashMap<>();
    private Map<Ref.Global,IdSet.Builder> _globalRefs = new HashMap<>();

    @Override public void openSession () {
      _writeCount = 0;
    }

    @Override public void closeSession () {
      // store our use indices
      for (Map.Entry<Long,IdSet.Builder> ent : _localRefs.entrySet()) {
        Long refId = ent.getKey();
        IdSet units = _locUseSrcs.getOrDefault(refId, IdSet.EMPTY);
        IdSet.Builder nunitsB = ent.getValue();
        nunitsB.add(units);
        IdSet nunits = nunitsB.build();
        if (!units.equals(nunits)) _locUseSrcs.put(refId, nunits);
      }
      _localRefs.clear();
      for (Map.Entry<Ref.Global,IdSet.Builder> ent : _globalRefs.entrySet()) {
        String refId = ent.getKey().toString();
        IdSet units = _gloUseSrcs.getOrDefault(refId, IdSet.EMPTY);
        IdSet.Builder nunitsB = ent.getValue();
        nunitsB.add(units);
        IdSet nunits = nunitsB.build();
        if (!units.equals(nunits)) _gloUseSrcs.put(refId, nunits);
      }
      _globalRefs.clear();

      // and finally commit all remaining writes
      _db.commit();
    }

    @Override protected void storeUnit (Source source, DefInfo topDef) {
      long indexed = System.currentTimeMillis(); // note the time

      // resolve the unit id for this source
      String srcKey = source.toString();
      Long unitId = _srcToId.computeIfAbsent(srcKey, unused -> _srcToId.size()+1L);

      // load the ids of existing defs in this source
      IdSet oldSourceIds = _srcDefs.getOrDefault(unitId, IdSet.EMPTY);
      IdSet.Builder newSourceIdsB = new IdSet.Builder();

      // track all refs to defs defined outside this compunit
      Set<Long> localRefs = new HashSet<>();
      Set<Ref.Global> globalRefs = new HashSet<>();

      // wrap this computation up in an object so that we don't have to pass so damned many
      // arguments around; yay for lack of nested functions
      new Runnable() {
        long maxExpId = toDefId(unitId, MAX_EXP_ID), maxNonExpId = toDefId(unitId, MAX_DEF_ID);
        Long nextExpId = nextUnusedExpId(toDefId(unitId, 0)), nextNonExpId = maxExpId+1;

        Long nextUnusedExpId (Long after) {
          long id = after + 1;
          while (oldSourceIds.contains(id)) id += 1;
          if (id > maxExpId) throw new IllegalStateException(
            "Ack! Can't support more than "+ MAX_EXP_ID +" exported defs per source file.");
          return id;
        }

        // create an empty ref tree for non-exported ref resolution
        RefTree sourceRefs = new EphemeralRefTree();

        Long resolveSourceId (Ref.Global id) {
          Long defId = sourceRefs.resolve(id, nextNonExpId);
          if (defId.equals(nextNonExpId)) {
            nextNonExpId += 1;
            if (nextNonExpId > maxNonExpId) throw new IllegalStateException(
              "Ack! Can't support more than "+ MAX_DEF_ID +" defs per source file.");
          }
          return defId;
        }

        Long resolveProjectId (Ref.Global id) {
          Long defId = _projectRefs.resolve(id, nextExpId);
          if (defId.equals(nextExpId)) nextExpId = nextUnusedExpId(defId);
          return defId;
        }

        void storeDef (DefInfo inf) {
          Long defId = inf.exported ? resolveProjectId(inf.id) : resolveSourceId(inf.id);
          Def def = inf.toDef(MapDBStore.this, defId, inf.outer.defId);
          _defs.put(def.id, def);
          newSourceIdsB.add(def.id);
          if (def.outerId == null) _topDefs.add(def.id);
          _indices.get(def.kind).add(Fun.t2(def.name.toLowerCase(), def.id));
        }

        void storeDefs (Iterable<DefInfo> defs) {
          if (defs != null) for (DefInfo def : defs) {
            storeDef(def);
            storeDefs(def.defs); // this will populate def.memDefIds with our member def ids

            // now update our member def ids mapping
            IdSet.Builder memDefIdsB = def.memDefIds;
            // if this def spans source files, do more complex member def merging
            if (defSpansSources(def)) {
              IdSet oldMemDefIds = _defMems.getOrDefault(def.defId, IdSet.EMPTY);
              for (long id : oldMemDefIds.elems) {
                if (!oldSourceIds.contains(id)) memDefIdsB.add(id);
              }
            }
            IdSet memDefIds = (memDefIdsB == null) ? IdSet.EMPTY : memDefIdsB.build();
            if (memDefIds.isEmpty()) _defMems.remove(def.defId);
            else _defMems.put(def.defId, memDefIds);
          }
        }

        boolean defSpansSources (DefInfo def) {
          return def.kind == Kind.MODULE; // TODO: have DefInfo self-report?
        }

        void storeData (DefInfo inf) {
          if (inf.sig != null) {
            _defSig.put(inf.defId, new Sig(inf.sig.text, resolveUses(inf.sig.uses)));
          }

          if (inf.doc != null) {
            List<Use> uses = resolveUses(inf.doc.uses);
            _defDoc.put(inf.defId, new Doc(inf.doc.offset, inf.doc.length, uses));
          }

          if (inf.uses == null) _defUses.remove(inf.defId);
          else {
            List<Use> uses = resolveUses(inf.uses);
            _defUses.put(inf.defId, uses);
            // record all refs made from this compunit
            for (Use use : uses) {
              Ref ref = use.ref();
              if (ref instanceof Ref.Local) {
                // omit refs to defs that originated in this compunit; when searching for refs to a
                // def, we always include its defining compunit in the search, so we don't need to
                // add that compunit to the index; this drastically reduces the size of the index
                // because the vast majority of defs are not referenced outside their compunit
                Long defId = ((Ref.Local)ref).defId;
                if (toUnitId(defId) != unitId) localRefs.add(defId);
              } else globalRefs.add((Ref.Global)ref);
            }
          }

          // TODO: store inf.relations
        }

        List<Use> resolveUses (List<UseInfo> infos) {
          if (infos == null) return Collections.emptyList();
          List<Use> uses = new ArrayList<>();
          for (UseInfo info : infos) {
            Long tgtId = sourceRefs.get(info.ref);
            if (tgtId == null) tgtId = _projectRefs.get(info.ref);
            uses.add(info.resolve(MapDBStore.this, tgtId));
          }
          return uses;
        }

        void storeDatas (Iterable<DefInfo> defs) {
          if (defs != null) for (DefInfo def : defs) {
            storeData(def);
            storeDatas(def.defs);
          }
        }

        public void run () {
          // first assign ids to all the defs and store the basic def data
          storeDefs(topDef.defs);
          // then go through and store additional data like sigs, docs and uses; now that all the
          // defs are stored and IDed, we can resolve many use refs to more compact local refs
          storeDatas(topDef.defs);
        }
      }.run();

      // accumulate to the indices for refs made in this compunit
      for (Long refId : localRefs) {
        _localRefs.computeIfAbsent(refId, rf -> IdSet.builder()).add(unitId);
      }
      for (Ref.Global ref : globalRefs) {
        _globalRefs.computeIfAbsent(ref, rf -> IdSet.builder()).add(unitId);
      }

      // filter the reused source ids from the old source ids and delete any that remain
      IdSet newSourceIds = newSourceIdsB.build();
      IdSet staleIds = oldSourceIds.minus(newSourceIds);
      if (!staleIds.isEmpty()) removeDefs(staleIds);
      _srcDefs.put(unitId, newSourceIds);
      _srcInfo.put(unitId, new IO.SourceInfo(srcKey, indexed));

      if (++_writeCount > COMMIT_EVERY) {
        _db.commit();
        _writeCount = 0;
      }

      // System.err.println(srcKey + " has " + newSourceIds.size() + " defs");
    }
  };

  public MapDBStore (String name) {
    this(name, null, DBMaker.newMemoryDB());
  }

  public MapDBStore (String name, Path store) {
    this(name, store, DBMaker.newFileDB(store.toFile()).
         mmapFileEnableIfSupported().
         cacheDisable().
         compressionEnable().
         asyncWriteEnable().
         closeOnJvmShutdown());
  }

  /**
   * Wipes the contents of this store, preparing it to be rebuild from scratch.
   */
  public void clear () {
    _projectRefs.clear();
    _srcToId.clear();
    _srcInfo.clear();
    _srcDefs.clear();
    _topDefs.clear();
    _defs.clear();
    _defMems.clear();
    _defUses.clear();
    _defSig.clear();
    _defDoc.clear();
  }

  public int defCount () {
    return _defs.size();
  }

  public int nameCount () {
    return _refsById.size();
  }

  @Override public void close () {
    _db.close();
  }

  @Override public Writer writer () {
    return writer;
  }

  @Override public Iterable<Def> topLevelDefs () {
    return defs(null, _topDefs);
  }

  @Override public long lastIndexed (Source source) {
    Long unitId = _srcToId.get(source.toString());
    if (unitId == null) return 0L;
    IO.SourceInfo info = _srcInfo.get(unitId);
    return info == null ? 0L : info.indexed;
  }

  @Override public Iterable<Def> sourceDefs (Source source) {
    Long unitId = _srcToId.get(source.toString());
    if (unitId == null) throw new IllegalArgumentException("Unknown source " + source);
    return Iterables.transform(_srcDefs.get(unitId), this::def);
  }

  @Override public Optional<Def> def (Ref.Global ref) {
    Long id = _projectRefs.get(ref);
    // TODO: we should probably freak out if we have an id mapping but no def...
    return (id == null) ? Optional.empty() : Optional.ofNullable(_defs.get(id));
  }

  @Override public Ref.Global ref (Long defId) {
    return _projectRefs.get(defId);
  }

  @Override public Def def (Long defId) {
    return reqdef(defId, _defs.get(defId));
  }

  @Override public Iterable<Def> defsIn (Long defId) {
    return Iterables.transform(_defMems.getOrDefault(defId, IdSet.EMPTY), this::def);
  }

  @Override public List<Use> usesIn (Long defId) {
    List<Use> uses = _defUses.get(defId);
    return (uses == null) ? Collections.emptyList() : uses;
  }

  @Override public Set<Ref> relationsFrom (Relation rel, Long defId) {
    return _defRelsFrom.get(rel).get(defId);
  }

  @Override public Set<Def> relationsTo (Relation rel, Ref ref) {
    Set<Def> defs = new HashSet<>();
    for (Long defId : _defRelsTo.get(rel).get(ref.toString())) defs.add(def(defId));
    return defs;
  }

  @Override public Map<Source,int[]> usesOf (Def def) {
    // determine whether we're looking for local or global refs
    Ref ref;
    IdSet unitIds;
    if (def.project == this) {
      ref = def.ref();
      // since this def is local to this project, the comp unit that defines it is "implicit" in its
      // unit ids set; so start with that, and then add any other unit ids that reference it
      unitIds = _locUseSrcs.getOrDefault(def.id, IdSet.EMPTY).plus(toUnitId(def.id));
    } else {
      ref = def.globalRef();
      unitIds = _gloUseSrcs.get(ref.toString());
    }
    if (unitIds == null) return Collections.emptyMap();

    Map<Source,int[]> uses = new HashMap<>();
    for (Long unitId : unitIds) {
      IO.SourceInfo info = _srcInfo.get(unitId);
      if (info == null) {
        System.err.println("Def reports use in non-existent source " +
                           "[def=" + def + ", unitId=" + unitId + "]");
        continue;
      }

      // right now we brute force our way through all uses in matching comp units; if that turns out
      // to be too expensive, we can include enclosing def ids in our index
      List<Use> srcUses = new ArrayList<>();
      for (Long defId : _srcDefs.get(unitId)) for (Use use : usesIn(defId)) {
        if (use.ref().equals(ref)) srcUses.add(use);
      }
      int[] offsets = new int[srcUses.size()];
      int ii = 0; for (Use use : srcUses) offsets[ii++] = use.offset();
      uses.put(Source.fromString(info.source), offsets);
    }
    return uses;
  }

  @Override public Optional<Sig> sig (Long defId) {
    return Optional.ofNullable(_defSig.get(defId));
  }

  @Override public Optional<Doc> doc (Long defId) {
    return Optional.ofNullable(_defDoc.get(defId));
  }

  @Override public Source source (Long defId) {
    IO.SourceInfo info = _srcInfo.get(toUnitId(defId));
    if (info == null) throw new IllegalArgumentException("No source for def " + idToString(defId));
    return Source.fromString(info.source);
  }

  @Override public String idToString (Long id) {
    long defId = id;
    long unitId = defId / MAX_DEF_ID;
    long rawDefId = defId % MAX_DEF_ID;
    String pre = (rawDefId > MAX_EXP_ID) ? "" : "exp:";
    if (rawDefId > MAX_EXP_ID) rawDefId -= MAX_EXP_ID;
    return pre + unitId + ":" + rawDefId;
  }

  @Override public void find (Query query, boolean expOnly, List<Def> into) {
    boolean pre = query.prefix;
    String name = query.name;
    System.err.println("Seeking " + query + " in " + this);
    Fun.Tuple2<String,Long> lowKey = Fun.t2(name, null);
    for (Kind kind : query.kinds) {
      NavigableSet<Fun.Tuple2<String,Long>> index = _indices.get(kind);
      for (Fun.Tuple2<String,Long> ent : _indices.get(kind).tailSet(lowKey)) {
        if ((pre && !ent.a.startsWith(name)) || (!pre && !ent.a.equals(name))) break;
        Def def = _defs.get(ent.b);
        if (def == null) continue; // index can contain stale entries
        // TODO: validate that def matches query (index may have stale link to reused def id)
        if (!expOnly || def.exported) {
          System.err.println("Found " + ent + " / " + def + " in " + this);
          into.add(def);
        }
      }
    }
  }

  private void removeDefs (IdSet defIds) {
    // TODO: only remove exported defs from projectRefs
    _projectRefs.remove(defIds);
    _topDefs.removeAll(defIds);
    _defs.keySet().removeAll(defIds);
    _defMems.keySet().removeAll(defIds);
    _defUses.keySet().removeAll(defIds);
    _defSig.keySet().removeAll(defIds);
    _defDoc.keySet().removeAll(defIds);
    // TODO: load all these defs and remove them from the indexes?
  }

  private Iterable<Def> defs (Long defId, Iterable<Long> defIds) {
    reqdef(defId, defIds);
    return Iterables.transform(defIds, this::def);
  }

  private <T> T reqdef (Long defId, T value) {
    if (value == null) throw new NoSuchElementException("No def with id " + idToString(defId));
    return value;
  }

  private MapDBStore (String name, Path storePath, DBMaker<?> maker) {
    super(name);
    BTreeKeySerializer<Long> longSz = BTreeKeySerializer.ZERO_OR_POSITIVE_LONG;
    BTreeKeySerializer<String> stringSz = BTreeKeySerializer.STRING;

    // if we're a persistent database, check our schema version and blow away the old db if the
    // schema is out of date; since a store is basically a fancy cache, it'll be rebuilt
    if (storePath != null) {
      Path versFile = Paths.get(storePath.toString()+".v");
      int fileVers = 0;
      try {
        if (Files.exists(versFile)) {
          fileVers = Integer.parseInt(Files.readAllLines(versFile).get(0));
        }
      } catch (Throwable t) {
        System.err.println("Error reading version from " + versFile);
        t.printStackTrace(System.err);
      }
      if (fileVers < SCHEMA_VERS) {
        String storeName = storePath.getFileName().toString();
        assert storeName.length() > 0;
        try {
          Path parent = storePath.getParent();
          if (parent != null && Files.exists(parent)) {
            for (Path file : Files.list(parent).collect(Collectors.toList())) {
              if (file.getFileName().toString().startsWith(storeName)) Files.delete(file);
            }
          }
        } catch (IOException ioe) {
          System.err.println("Error deleting stale database: " + ioe);
        }
        try {
          Files.write(versFile, Arrays.asList(String.valueOf(SCHEMA_VERS)));
        } catch (IOException ioe) {
          System.err.println("Error writing version file " + versFile + ": " + ioe);
        }
      }
    }

    // MapDB insists on serializing they key and value serializers into a catalog file, so we have
    // to do this hackery to ensure that our serialized serializers get the right store reference;
    // this also has to happen *before* we call maker.make() because the classes are resolved then
    Thread thread = Thread.currentThread();
    ClassLoader oloader = thread.getContextClassLoader();
    thread.setContextClassLoader(getClass().getClassLoader());
    IO.store = this;
    try {
      _db = maker.make();

      _srcToId = createTreeMap("srcToId", stringSz, Serializer.LONG);
      _srcDefs = createTreeMap("srcDefs", longSz, IO.IDS_SZ);
      _srcInfo = createTreeMap("srcInfo", longSz, IO.SRCINFO_SZ);
      _topDefs = _db.createTreeSet("topDefs").serializer(longSz).makeOrGet();

      _defs    = createTreeMap("defs",    longSz, new IO.DefSerializer());
      _defSig  = createTreeMap("defSig",  longSz, new IO.SigSerializer());
      _defDoc  = createTreeMap("defDoc",  longSz, new IO.DocSerializer());
      _defMems = createTreeMap("defMems", longSz, IO.IDS_SZ);
      _defUses = createTreeMap("defUses", longSz, new IO.UsesSerializer());

      _defRelsFrom = new HashMap<>();
      _defRelsTo = new HashMap<>();
      IO.RefSetSerializer refSz = new IO.RefSetSerializer();
      for (Relation rel : Relation.values()) {
        _defRelsFrom.put(rel, createTreeMap("defRelsFrom" + rel, longSz, refSz));
        _defRelsTo.put(rel, createTreeMap("defRelsTo" + rel, stringSz, IO.IDS_SZ));
      }

      _locUseSrcs = createTreeMap("locUseSrcs", longSz, IO.IDS_SZ);
      _gloUseSrcs = createTreeMap("gloUseSrcs", stringSz, IO.IDS_SZ);

      _indices = new HashMap<>();
      for (Kind kind : Kind.values()) {
        _indices.put(kind, _db.createTreeSet("idx"+kind).serializer(
          BTreeKeySerializer.TUPLE2).makeOrGet());
      }

      // TODO: omit refsById and resolve global name using def chain
      _refsByName = createTreeMap("refsByName", stringSz, Serializer.LONG);
      _refsById   = createTreeMap("refsById", longSz, Serializer.STRING);
      _projectRefs = new RefTree() {
        public Long get (Ref.Global ref) {
          return _refsByName.get(ref.toString());
        }
        public Ref.Global get (Long defId) {
          String sv = _refsById.get(defId);
          return (sv == null) ? null : Ref.Global.fromString(sv);
        }
        public Long resolve (Ref.Global ref, Long assignId) {
          String key = ref.toString();
          Long id = _refsByName.get(key);
          if (id == null) {
            _refsByName.put(key, assignId);
            _refsById.put(assignId, key);
            id = assignId;
          }
          return id;
        }
        public void remove (Iterable<Long> ids) {
          for (Long id : ids) {
            String ref = _refsById.remove(id);
            if (ref != null) _refsByName.remove(ref);
          }
        }
        public void clear () {
          _refsByName.clear();
          _refsById.clear();
        }
      };

    } finally {
      IO.store = null;
      thread.setContextClassLoader(oloader);
    }
  }

  private <K,V> BTreeMap<K,V> createTreeMap (String name, BTreeKeySerializer<K> keySz,
                                             Serializer<V> valSz) {
    return _db.createTreeMap(name).keySerializer(keySz).valueSerializer(valSz).makeOrGet();
  }

  private final DB _db;

  private final BTreeMap<String,Long> _srcToId;
  private final BTreeMap<Long,IO.SourceInfo> _srcInfo;
  private final BTreeMap<Long,IdSet> _srcDefs;
  private final Set<Long> _topDefs;
  private final BTreeMap<Long,Def> _defs;
  private final BTreeMap<Long,Sig> _defSig;
  private final BTreeMap<Long,Doc> _defDoc;
  private final BTreeMap<Long,IdSet> _defMems;
  private final BTreeMap<Long,List<Use>> _defUses;
  private final Map<Relation,BTreeMap<Long,Set<Ref>>> _defRelsFrom;
  private final Map<Relation,BTreeMap<String,IdSet>> _defRelsTo;
  private final Map<Kind,NavigableSet<Fun.Tuple2<String,Long>>> _indices;
  private final BTreeMap<Long,IdSet> _locUseSrcs;
  private final BTreeMap<String,IdSet> _gloUseSrcs;

  private final BTreeMap<String,Long> _refsByName;
  private final BTreeMap<Long,String> _refsById;
  private final RefTree _projectRefs;

  private static final int SCHEMA_VERS = 2;
}
