//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.store;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.PropertyContainer;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.ResourceIterable;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.graphdb.schema.Schema;
import org.neo4j.tooling.GlobalGraphOperations;

import codex.extract.BatchWriter;
import codex.extract.Writer;
import codex.model.*;

public class Neo4jStore extends ProjectStore {

  // define our node types
  private enum Entity implements Label { ROOT_NAME, NAME, UNIT, TOP_DEF, DEF, USE, SIG, DOC; }

  // define our relationship types
  private static enum Edge implements RelationshipType {
    NAME_NAME, // links a name to its child name (1 to many)
    NAME_DEF,  // links a name to its def (1 to 1)
    UNIT_DEF,  // all the defs in a unit (1 to many)
    DEF_DEF,   // all the defs nested directly in an outer def (1 to many)
    DEF_SIG,   // the sig for a def (1 to 1)
    DEF_DOC,   // the doc for a def (1 to 1)
    DEF_USE,   // all the uses in a def (1 to many)
    SIG_USE,   // all the uses in a sig (1 to many)
    DOC_USE,   // all the uses in a doc (1 to many)
    TODO;
  }

  // define our node properties
  private static class NodeProp<T> {
    public final String name;
    public NodeProp (String name) {
      this.name = name;
    }
    public T get (PropertyContainer node) { return get(node, null); }
    public T get (PropertyContainer node, T defaultValue) {
      return unmarshal(node.getProperty(name, defaultValue));
    }
    public void set (PropertyContainer node, T value) {
      node.setProperty(name, marshal(value));
    }
    protected Object marshal (T value) { return value; }
    protected T unmarshal (Object value) { return (T)value; }
  }
  private static final NodeProp<String>  SOURCE = new NodeProp<String>("source");
  private static final NodeProp<Long>    OUTER_ID = new NodeProp<Long>("outerId");
  private static final NodeProp<String>  NAME = new NodeProp<String>("name");
  private static final NodeProp<Boolean> EXPORTED = new NodeProp<Boolean>("exported");
  private static final NodeProp<Integer> OFFSET = new NodeProp<Integer>("offset");
  private static final NodeProp<Integer> LENGTH = new NodeProp<Integer>("length");
  private static final NodeProp<Integer> BODY_START = new NodeProp<Integer>("bodyStart");
  private static final NodeProp<Integer> BODY_END = new NodeProp<Integer>("bodyEnd");
  private static final NodeProp<String>  TEXT = new NodeProp<String>("text");
  private static final NodeProp<Long>    LAST_INDEXED = new NodeProp<Long>("lastIndexed");

  private static class EnumProp<T extends Enum<T>> extends NodeProp<T> {
    public final Class<T> enumClass;
    public EnumProp (String name, Class<T> enumClass) {
      super(name);
      this.enumClass = enumClass;
    }
    protected Object marshal (T value) { return value.name(); }
    protected T unmarshal (Object value) { return Enum.valueOf(enumClass, String.valueOf(value)); }
  }
  private static final EnumProp<Kind>   KIND   = new EnumProp<Kind>("kind", Kind.class);
  private static final EnumProp<Flavor> FLAVOR = new EnumProp<Flavor>("flavor", Flavor.class);
  private static final EnumProp<Access> ACCESS = new EnumProp<Access>("access", Access.class);

  /** A writer that can be used to write metadata to this store. Handles incremental updates. */
  public final Writer writer = new BatchWriter() {

    private Transaction _tx;
    private int _writeCount; // used to commit every 100 compilation units
    private static final int COMMIT_EVERY = 10;

    @Override public void openSession () {
      _writeCount = 0;
      _tx = _db.beginTx();
    }

    @Override public void closeSession () {
      // finally commit all remaining writes
      _tx.success();
      _tx.close();
    }

    @Override protected void storeUnit (Source source, DefInfo topDef) {
      long indexed = System.currentTimeMillis(); // note the time

      // resolve the unit node for this source
      String srcKey = source.toString();
      Node unitn = findNode(Entity.UNIT, SOURCE.name, srcKey);
      if (unitn == null) {
        unitn = _db.createNode(Entity.UNIT);
        System.out.println("Created UNIT node (" + source + ") " + unitn.getId());
        SOURCE.set(unitn, srcKey);
      }
      LAST_INDEXED.set(unitn, indexed);

      // traverse the def tree from the top-level and store all the defs &c
      storeDefs(unitn, topDef.defs);

      if (++_writeCount > COMMIT_EVERY) {
        _tx.success();
        _tx.close();
        _tx = _db.beginTx();
        _writeCount = 0;
      }

      // System.err.println(srcKey + " has " + newSourceIds.size() + " defs");
    }

    private void storeDefs (Node unitn, Iterable<DefInfo> defs) {
      if (defs != null) for (DefInfo def : defs) {
        storeDef(unitn, def);
        storeDefs(unitn, def.defs); // this will populate def.memDefIds with our member def ids
      }
    }

    private void storeDef (Node unitn, DefInfo inf) {
      Node namen = resolveName(inf.id, inf.kind, true);
      Relationship drel = namen.getSingleRelationship(Edge.NAME_DEF, Direction.OUTGOING);
      Node defn;
      if (drel != null) defn = drel.getEndNode();
      else {
        defn = _db.createNode(Entity.DEF);
        // System.out.println("Created DEF node (" + inf.id + ") " + defn.getId());
        unitn.createRelationshipTo(defn, Edge.UNIT_DEF);
        namen.createRelationshipTo(defn, Edge.NAME_DEF);
        if (inf.outer.defId == null) defn.addLabel(Entity.TOP_DEF);
        else _db.getNodeById(inf.outer.defId).createRelationshipTo(defn, Edge.DEF_DEF);
      }
      inf.defId = defn.getId();

      NAME.set(defn, inf.name);
      KIND.set(defn, inf.kind);
      FLAVOR.set(defn, inf.flavor);
      EXPORTED.set(defn, inf.exported);
      ACCESS.set(defn, inf.access);
      OFFSET.set(defn, inf.offset);
      BODY_START.set(defn, inf.bodyStart);
      BODY_END.set(defn, inf.bodyEnd);

      // store sig info
      if (inf.sig == null) deleteVia(defn, Edge.DEF_SIG);
      else {
        Node sign = getOrCreate(defn, Edge.DEF_SIG, Entity.SIG);
        TEXT.set(sign, inf.sig.text);
        storeUseLinks(sign, Edge.SIG_USE, inf.sig.uses);
      }

      // store doc info
      if (inf.doc == null) deleteVia(defn, Edge.DEF_DOC);
      else {
        Node docn = getOrCreate(defn, Edge.DEF_DOC, Entity.DOC);
        OFFSET.set(docn, inf.doc.offset);
        LENGTH.set(docn, inf.doc.length);
        storeUseLinks(docn, Edge.DOC_USE, inf.doc.uses);
      }

      // store use info
      storeUseLinks(defn, Edge.DEF_USE, inf.uses);
    }

    private void storeUseLinks (Node srcn, Edge edge, List<UseInfo> uses) {
      // TODO: delete old use links
      if (uses == null) return;
      for (UseInfo use : uses) {
        Node namen = resolveName(use.ref, use.refKind, true);
        Relationship rel = srcn.createRelationshipTo(namen, edge);
        OFFSET.set(rel, use.offset);
        // TODO: set length if it differs from namen.name.length?
      }
    }

    private void deleteVia (Node srcn, Edge edge /*TODO, Edge... rels*/) {
      Relationship rel = srcn.getSingleRelationship(edge, Direction.OUTGOING);
      if (rel != null) {
        Node tgtn = rel.getEndNode();
        rel.delete();
        // TODO: for (Edge rel : rels) deleteLinks(tgtn, rel);
        tgtn.delete();
      }
    }

    private Node getOrCreate (Node srcn, Edge edge, Entity entity) {
      Relationship rel = srcn.getSingleRelationship(edge, Direction.OUTGOING);
      if (rel != null) {
        // TODO: delete pre-existing relationships? reuse them?
        return rel.getEndNode();
      }
      else {
        Node tgtn = _db.createNode(entity);
        srcn.createRelationshipTo(tgtn, edge);
        return tgtn;
      }
    }
  };

  public Neo4jStore (String name, Path store) {
    super(name);
    _store = store;
    // TODO: do we need to track schema version?
    // // if we're a persistent database, check our schema version and blow away the old db if the
    // // schema is out of date; since a store is basically a fancy cache, it'll be rebuilt
    // if (storePath != null) {
    //   Path versFile = Paths.get(storePath.toString()+".v");
    //   int fileVers = 0;
    //   try {
    //     if (Files.exists(versFile)) {
    //       fileVers = Integer.parseInt(Files.readAllLines(versFile).get(0));
    //     }
    //   } catch (Throwable t) {
    //     System.err.println("Error reading version from " + versFile);
    //     t.printStackTrace(System.err);
    //   }
    //   if (fileVers < SCHEMA_VERS) {
    //     String storeName = storePath.getFileName().toString();
    //     assert storeName.length() > 0;
    //     try {
    //       Path parent = storePath.getParent();
    //       if (parent != null && Files.exists(parent)) {
    //         for (Path file : Files.list(parent).collect(Collectors.toList())) {
    //           if (file.getFileName().toString().startsWith(storeName)) Files.delete(file);
    //         }
    //       }
    //     } catch (IOException ioe) {
    //       System.err.println("Error deleting stale database: " + ioe);
    //     }
    //     try {
    //       Files.write(versFile, Arrays.asList(String.valueOf(SCHEMA_VERS)));
    //     } catch (IOException ioe) {
    //       System.err.println("Error writing version file " + versFile + ": " + ioe);
    //     }
    //   }
    // }

    _db = new GraphDatabaseFactory().newEmbeddedDatabaseBuilder(store.toString()).
      setConfig(GraphDatabaseSettings.keep_logical_logs, "10 txs").
      newGraphDatabase();

    // if we have no root name, we need to create the database
    try (Transaction tx = _db.beginTx()) {
      _rootName = findNode(Entity.NAME, NAME.name, ROOT_NAME_NAME);
      tx.success();
    }
    if (_rootName == null) createDB();
  }

  /**
   * Wipes the contents of this store, preparing it to be rebuild from scratch.
   */
  public void clear () {
    _db.shutdown();
    try {
      Files.delete(_store);
    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }
    _db = new GraphDatabaseFactory().newEmbeddedDatabase(_store.toString());
    createDB();
  }

  public int defCount () {
    try (Transaction tx = _db.beginTx()) {
      int result = 0;
      for (Node node : GlobalGraphOperations.at(_db).getAllNodesWithLabel(Entity.DEF)) {
        result += 1;
      }
      tx.success();
      return result;
    }
  }

  public int nameCount () {
    try (Transaction tx = _db.beginTx()) {
      int result = 0;
      for (Node node : GlobalGraphOperations.at(_db).getAllNodesWithLabel(Entity.NAME)) {
        result += 1;
      }
      tx.success();
      return result;
    }
  }

  @Override public void close () {
    _db.shutdown();

    // delete annoying logical logs; Neo4j refuses to honor its settings that turn them off
    try {
      Files.list(_store).
        filter(path -> path.getFileName().toString().startsWith("nioneo_logical.log.v")).
        forEach(path -> {
          try { Files.delete(path); }
          catch (IOException ioe) { ioe.printStackTrace(System.err); }
        });
    } catch (IOException ioe) {
      ioe.printStackTrace(System.err);
    }
  }

  @Override public Writer writer () {
    return writer;
  }

  private Def makeDef (Node node) {
    return new Def(
      this, node.getId(), OUTER_ID.get(node, 0L), KIND.get(node), FLAVOR.get(node),
      EXPORTED.get(node), ACCESS.get(node), NAME.get(node),
      OFFSET.get(node), BODY_START.get(node), BODY_END.get(node));
  }

  @Override public Iterable<Def> topLevelDefs () {
    try (Transaction tx = _db.beginTx()) {
      ResourceIterable<Node> topDefNs = GlobalGraphOperations.at(_db).
        getAllNodesWithLabel(Entity.TOP_DEF);
      Iterable<Def> result = Lists.newArrayList(Iterables.transform(topDefNs, this::makeDef));
      tx.success();
      return result;
    }
  }

  @Override public long lastIndexed (Source source) {
    try (Transaction tx = _db.beginTx()) {
      Node unitn = findNode(Entity.UNIT, SOURCE.name, source.toString());
      long result = unitn == null ? 0L : LAST_INDEXED.get(unitn, 0L);
      tx.success();
      return result;
    }
  }

  @Override public Iterable<Def> sourceDefs (Source source) {
    try (Transaction tx = _db.beginTx()) {
      Node unitn = findNode(Entity.UNIT, SOURCE.name, source.toString());
      if (unitn == null) throw new IllegalArgumentException("Unknown source " + source);
      Iterable<Def> result = Iterables.transform(
        unitn.getRelationships(Edge.UNIT_DEF, Direction.OUTGOING),
        r -> makeDef(r.getEndNode()));
      tx.success();
      return result;
    }
  }

  @Override public Optional<Def> def (Ref.Global ref) {
    try (Transaction tx = _db.beginTx()) {
      Optional<Def> result = Optional.ofNullable(resolveName(ref, null, false)).
        map(n -> n.getSingleRelationship(Edge.NAME_DEF, Direction.OUTGOING)).
        map(r -> makeDef(r.getEndNode()));
      tx.success();
      return result;
    }
  }

  @Override public Def def (Long defId) {
    try (Transaction tx = _db.beginTx()) {
      Def result = makeDef(reqdef(defId));
      tx.success();
      return result;
    }
  }

  @Override public Ref.Global ref (Long defId) {
    try (Transaction tx = _db.beginTx()) {
      Ref.Global result = globalRef(nameForDef(defId));
      tx.success();
      return result;
    }
  }

  @Override public Optional<Sig> sig (Long defId) {
    try (Transaction tx = _db.beginTx()) {
      Relationship rel = reqdef(defId).getSingleRelationship(Edge.DEF_SIG, Direction.OUTGOING);
      Optional<Sig> result = Optional.ofNullable(rel).map(r -> {
        Node sign = r.getEndNode();
        return new Sig(TEXT.get(sign), Lists.newArrayList(getUses(sign, Edge.SIG_USE)));
      });
      tx.success();
      return result;
    }
  }

  @Override public Optional<Doc> doc (Long defId) {
    try (Transaction tx = _db.beginTx()) {
      Relationship rel = reqdef(defId).getSingleRelationship(Edge.DEF_DOC, Direction.OUTGOING);
      Optional<Doc> result = Optional.ofNullable(rel).map(r -> {
        Node docn = r.getEndNode();
        return new Doc(OFFSET.get(docn), LENGTH.get(docn),
                       Lists.newArrayList(getUses(docn, Edge.DOC_USE)));
      });
      tx.success();
      return result;
    }
  }

  @Override public Source source (Long defId) {
    try (Transaction tx = _db.beginTx()) {
      Node unitn = reqdef(defId).getSingleRelationship(Edge.UNIT_DEF, Direction.INCOMING).
        getEndNode();
      Source result = Source.fromString(SOURCE.get(unitn));
      tx.success();
      return result;
    }
  }

  @Override public Iterable<Def> defsIn (Long defId) {
    try (Transaction tx = _db.beginTx()) {
      Iterable<Def> result = Iterables.transform(
        reqdef(defId).getRelationships(Edge.DEF_DEF, Direction.OUTGOING),
        r -> makeDef(r.getEndNode()));
      tx.success();
      return result;
    }
  }

  @Override public Iterable<Use> usesIn (Long defId) {
    try (Transaction tx = _db.beginTx()) {
      Iterable<Use> result = getUses(reqdef(defId), Edge.DEF_USE);
      tx.success();
      return result;
    }
  }

  @Override public Set<Ref> relationsFrom (Relation rel, Long defId) {
    try (Transaction tx = _db.beginTx()) {
      Set<Ref> result = Sets.newHashSet(Iterables.transform(
        reqdef(defId).getRelationships(rel, Direction.OUTGOING),
        r -> makeRef(r.getEndNode())));
      tx.success();
      return result;
    }
  }

  @Override public Set<Def> relationsTo (Relation rel, Ref ref) {
    try (Transaction tx = _db.beginTx()) {
      Node namen = (ref instanceof Ref.Local) ?
        nameForDef(((Ref.Local)ref).defId) : resolveName((Ref.Global)ref, null, false);
      Set<Def> result = (namen == null) ? Collections.emptySet() : Sets.newHashSet(
        Iterables.transform(namen.getRelationships(rel, Direction.INCOMING),
                            r -> makeDef(r.getStartNode())));
      tx.success();
      return result;
    }
  }

  @Override public Map<Source,int[]> usesOf (Def def) {
    return Collections.emptyMap(); // TODO
    // // determine whether we're looking for local or global refs
    // Ref ref;
    // IdSet unitIds;
    // if (def.project == this) {
    //   ref = def.ref();
    //   // since this def is local to this project, the comp unit that defines it is "implicit" in its
    //   // unit ids set; so start with that, and then add any other unit ids that reference it
    //   unitIds = _locUseSrcs.getOrDefault(def.id, IdSet.EMPTY).plus(toUnitId(def.id));
    // } else {
    //   ref = def.globalRef();
    //   unitIds = _gloUseSrcs.get(ref.toString());
    // }
    // if (unitIds == null) return Collections.emptyMap();

    // Map<Source,int[]> uses = new HashMap<>();
    // for (Long unitId : unitIds) {
    //   IO.SourceInfo info = _srcInfo.get(unitId);
    //   if (info == null) {
    //     System.err.println("Def reports use in non-existent source " +
    //                        "[def=" + def + ", unitId=" + unitId + "]");
    //     continue;
    //   }

    //   // right now we brute force our way through all uses in matching comp units; if that turns out
    //   // to be too expensive, we can include enclosing def ids in our index
    //   List<Use> srcUses = new ArrayList<>();
    //   for (Long defId : _srcDefs.get(unitId)) for (Use use : usesIn(defId)) {
    //     if (use.ref().equals(ref)) srcUses.add(use);
    //   }
    //   int[] offsets = new int[srcUses.size()];
    //   int ii = 0; for (Use use : srcUses) offsets[ii++] = use.offset();
    //   uses.put(Source.fromString(info.source), offsets);
    // }
    // return uses;
  }

  @Override public String idToString (Long id) {
    return String.valueOf(id);
  }

  @Override public void find (Query query, boolean expOnly, List<Def> into) {
    // TODO
    // boolean pre = query.prefix;
    // String name = query.name;
    // System.err.println("Seeking " + query + " in " + this);
    // Fun.Tuple2<String,Long> lowKey = Fun.t2(name, null);
    // for (Kind kind : query.kinds) {
    //   NavigableSet<Fun.Tuple2<String,Long>> index = _indices.get(kind);
    //   for (Fun.Tuple2<String,Long> ent : _indices.get(kind).tailSet(lowKey)) {
    //     if ((pre && !ent.a.startsWith(name)) || (!pre && !ent.a.equals(name))) break;
    //     Def def = _defs.get(ent.b);
    //     if (def == null) continue; // index can contain stale entries
    //     // TODO: validate that def matches query (index may have stale link to reused def id)
    //     if (!expOnly || def.exported) {
    //       System.err.println("Found " + ent + " / " + def + " in " + this);
    //       into.add(def);
    //     }
    //   }
    // }
  }

  private void createDB () {
    // create our indexes
    try (Transaction tx = _db.beginTx()) {
      Schema schema = _db.schema();
      schema.indexFor(Entity.UNIT).on("source").create();
      schema.indexFor(Entity.NAME).on("name").create();
      // TODO: other indices?
      tx.success();
    }

    // create our indexes
    try (Transaction tx = _db.beginTx()) {
      // create our root name node
      _rootName = _db.createNode(Entity.NAME);
      NAME.set(_rootName, ROOT_NAME_NAME);
      tx.success();
    }
  }

  private Node resolveName (Ref.Global id, Kind kind, boolean create) {
    if (id == Ref.Global.ROOT) return _rootName;
    else {
      // TODO: will we need to initialize kind on name nodes that were created as parents but then
      // turned out to be real nodes? I don't think so, but confirmation is needed...
      Node parent = resolveName(id.parent, null, create);
      for (Relationship r : parent.getRelationships(Edge.NAME_NAME, Direction.OUTGOING)) {
        if (NAME.get(r).equals(id.id)) return r.getEndNode();
      }
      if (!create) return null;
      // create a new name node, and wire it up to its parent with a named edge
      Node namen = _db.createNode(Entity.NAME);
      System.out.println("Created NAME node (" + id + ") " + namen.getId());
      Relationship r = parent.createRelationshipTo(namen, Edge.NAME_NAME);
      NAME.set(r, id.id);
      if (kind != null) KIND.set(r, kind);
      return namen;
    }
  }

  private Ref.Global globalRef (Node namen) {
    if (namen == _rootName) return Ref.Global.ROOT;
    else {
      Node namep = namen.getSingleRelationship(Edge.NAME_NAME, Direction.INCOMING).getStartNode();
      return globalRef(namep).plus(NAME.get(namen));
    }
  }

  private Ref makeRef (Node namen) {
    // if this name node has a NAME_DEF edge, it's a project local name; otherwise it's global
    Relationship rel = namen.getSingleRelationship(Edge.NAME_DEF, Direction.OUTGOING);
    if (rel == null) return globalRef(namen);
    else return Ref.local(this, rel.getEndNode().getId());
  }

  private Node nameForDef (Long defId) {
    return reqdef(defId).getSingleRelationship(Edge.NAME_DEF, Direction.INCOMING).getStartNode();
  }

  private Node findNode (Entity entity, String key, Object value) {
    try (ResourceIterator<Node> iter = _db.findNodesByLabelAndProperty(entity, key, value).iterator()) {
      if (iter.hasNext()) return iter.next();
    }
    return null;
  }

  private Iterable<Use> getUses (Node node, Edge edge) {
    return Iterables.transform(node.getRelationships(edge, Direction.OUTGOING), rel -> {
      Node namen = rel.getEndNode();
      return new Use(makeRef(namen), KIND.get(namen), OFFSET.get(rel),
                     LENGTH.get(rel, NAME.get(namen).length()));
    });
  }

  private Node reqdef (Long defId) {
    Node defn = _db.getNodeById(defId);
    if (defn == null) throw new NoSuchElementException("No def with id " + idToString(defId));
    return defn;
  }

  private final Path _store;
  // these are nearly final, only change in clear()
  private GraphDatabaseService _db;
  private Node _rootName;

  private static final String ROOT_NAME_NAME = "__Neo4jNameRoot__";
  private static final int SCHEMA_VERS = 1;
}
