//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.store

import codex.extract.BatchWriter
import codex.extract.Writer
import codex.model._
import java.io.{File, IOException}
import java.nio.file.{Files, Path, Paths}
import java.util.concurrent.atomic.AtomicLong
import java.util.stream.Collectors
import java.util.{Arrays, ArrayList, Collections, List => JList, HashMap, HashSet, Optional}
import org.mapdb.{BTreeKeySerializer, BTreeMap, Bind, DB, DBMaker, Fun, Serializer}
import scala.collection.mutable.{Builder, Map => MMap, Set => MSet}
import scaled._

class MapDBStore private (name :String, maker :DBMaker[_]) extends ProjectStore(name) {

  import scala.collection.convert.WrapAsScala.asScalaSet
  import scala.collection.convert.WrapAsJava.setAsJavaSet
  import BTreeKeySerializer.{
    ZERO_OR_POSITIVE_LONG => longSz, ZERO_OR_POSITIVE_INT => intSz, STRING => stringSz,
    Tuple2KeySerializer => T2KS, Tuple3KeySerializer => T3KS}
  import IO._

  /** Creates an ephemeral (memory backed) store. */
  def this (name :String) = this(name, DBMaker.newMemoryDB)

  /** Creates a persistent store backed by `storePath`. */
  def this (name :String, storePath :Path) = this(
    name, DBMaker.newFileDB(MapDBStore.checkSchema(storePath).toFile).
      mmapFileEnableIfSupported.
      cacheDisable.
      compressionEnable.
      asyncWriteEnable.
      closeOnJvmShutdown)

  // MapDB insists on serializing they key and value serializers into a catalog file, so we have
  // to do this hackery to ensure that our serialized serializers get the right store reference
  // this also has to happen *before* we call maker.make() because the classes are resolved then
  { IO.store = this
    val thread = Thread.currentThread
    val oloader = thread.getContextClassLoader
    thread.setContextClassLoader(getClass.getClassLoader)
  }
  private val _db = maker.make()

  private def createTreeMap[K,V] (name :String, kSz :BTreeKeySerializer[K], vSz :Serializer[V]) =
    _db.createTreeMap(name).keySerializer(kSz).valueSerializer(vSz).makeOrGet[K,V]

  private def createTreeSet[E] (name :String, eSz :BTreeKeySerializer[E]) =
    _db.createTreeSet(name).serializer(eSz).makeOrGet[E]

  private val _names = createTreeMap("names", longSz, NAME_SZ)
  private val _fqNames = createTreeMap("fqNames", stringSz, Serializer.LONG)

  // read the highest name value and seed our next name id counter
  private var _maxNameId = new AtomicLong(
    if (_names.isEmpty) 0L else _names.keySet().last().longValue)

  private val _srcToId = createTreeMap("srcToId", stringSz, Serializer.INTEGER) // source -> unitId
  private val _srcDefs = createTreeMap("srcDefs", intSz, IDS_SZ) // unitId -> Set(defId)
  private val _srcInfo = createTreeMap("srcInfo", intSz, SRCINFO_SZ)
  private val _topDefs = createTreeSet("topDefs", longSz)

  private val _defs    = createTreeMap("defs",    longSz, new DefSerializer())
  private val _defSig  = createTreeMap("defSig",  longSz, SIG_SZ)
  private val _defDoc  = createTreeMap("defDoc",  longSz, DOC_SZ)
  private val _defMems = createTreeMap("defMems", longSz, IDS_SZ)
  private val _defUses = createTreeMap("defUses", longSz, USES_SZ)

  private def relSz = new T3KS[Id,Integer,Id](null, null, null, null, null)
  private val _relsFrom = createTreeSet("relsFrom", relSz) // (defId, rel, nameId)
  private val _relsTo   = createTreeSet("relsTo",   relSz) // (nameId, rel, defId)

  private val _useBySrc = createTreeMap("useBySrc", longSz, INT_SET_SZ) // nameId -> Set(unitId)

  private val _indices = (Kind.values map { kind =>
    (kind -> createTreeSet("idx"+kind, new T2KS[String,Id](null, null, null)))
  }).toMap

  // now clean up our static mess, blah
  IO.store = null

  def defCount :Int = _defs.size
  def nameCount :Int = _fqNames.size

  override val writer = new BatchWriter() {
    import BatchWriter._

    // use this to commit every 100 compilation units keeps WAL from getting too big
    private[this] var _writeCount = 0
    private[this] val COMMIT_EVERY = 100
    private[this] val _nUseBySrc = new HashMap[Id,Set.Builder[Integer]]()

    override def openSession () {
      _writeCount = 0
    }

    override def closeSession () {
      // update our "uses by unit id" indices
      _nUseBySrc.toMapV foreach { (refId, nunitsB) =>
        val units = _useBySrc.getOrDefault(refId, Set())
        nunitsB ++= units
        val nunits = nunitsB.build()
        if (!units.equals(nunits)) _useBySrc.put(refId, nunits)
      }
      _nUseBySrc.clear()

      // and finally commit all remaining writes
      _db.commit()
    }

    override protected def storeUnit (source :Source, topDef :DefInfo) {
      val indexed = System.currentTimeMillis // note the time

      // resolve the unit id for this source
      val srcKey = source.toString()
      val unitId = Mutable.getOrPut[String,Integer](_srcToId, srcKey, _srcToId.size+1)

      // load the ids of existing defs in this source
      val oldSourceIds = _srcDefs.getOrDefault(unitId, NoIds)
      val newSourceIdsB = idSetBuilder

      // track all refs to defs defined outside this compunit
      val extRefs = MSet[Id]()

      // TODO: have DefInfo self-report?
      def defSpansSources (df :DefInfo) = df.kind == Kind.MODULE

      def resolveUses (infos :JList[UseInfo]) = infos match {
        case null => Seq()
        case infos =>
          val ub = Seq.builder[PUse](infos.size)
          for (ui <- infos) ub += PUse(resolveName(ui.ref, ui.refKind, 0), ui.offset, ui.length)
          ub.build()
      }

      def storeDef (inf :DefInfo) {
        val defId = toDefId(resolveName(inf.id, inf.kind, unitId), unitId)
        val df = inf.toDef(MapDBStore.this, defId, inf.outer.defId)
        _defs.put(df.id, df)
        newSourceIdsB += df.id
        if (df.outerId == null) _topDefs.add(df.id)
        _indices(df.kind).add(Fun.t2(df.name.toLowerCase, df.id))
      }

      def storeDefs (defs :JIterable[DefInfo]) {
        if (defs != null) defs foreach { df =>
          storeDef(df)
          storeDefs(df.defs) // this will populate def.memDefIds with our member def ids

          // now update our member def ids mapping
          val memDefIds = df.memDefIds
          // if this def spans source files, do more complex member def merging
          if (defSpansSources(df)) {
            val oldMemDefIds = _defMems.getOrDefault(df.defId, NoIds)
            oldMemDefIds foreach { id =>
              if (!oldSourceIds.contains(id)) memDefIds.add(id)
            }
          }
          val ids = if (memDefIds == null) NoIds else NoIds ++ asScalaSet(memDefIds)
          if (ids.isEmpty) _defMems.remove(df.defId)
          else _defMems.put(df.defId, ids)
        }
      }

      // first assign ids to all the defs and store the basic def data
      storeDefs(topDef.defs)

      // generate the set of all def ids in this compunit
      val newSourceIds = newSourceIdsB.result

      def storeData (inf :DefInfo) {
        val defId = inf.defId
        if (inf.sig != null) {
          _defSig.put(defId, PSig(inf.sig.text, resolveUses(inf.sig.uses)))
        }
        if (inf.doc != null) {
          _defDoc.put(defId, PDoc(inf.doc.offset, inf.doc.length, resolveUses(inf.doc.uses)))
        }

        if (inf.uses == null) _defUses.remove(defId)
        else {
          val uses = resolveUses(inf.uses)
          _defUses.put(defId, uses)
          // record all refs made from this compunit
          uses foreach { use =>
            // omit refs to defs that originated in this compunit when searching for refs to a def,
            // we always include its defining compunit in the search, so we don't need to add that
            // compunit to the index; this drastically reduces the size of the index because the
            // vast majority of defs are not referenced outside their compunit
            if (!newSourceIds(use.nameId)) extRefs += use.nameId
          }
        }

        // compute (defId, relstr, tgtNameId) for all of this def's relations
        val rels = if (inf.relations == null) Collections.emptySet() else {
          val rels = new HashSet[Fun.Tuple3[Id,Integer,Id]]()
          inf.relations.foreach { r =>
            rels.add(Fun.t3(defId, r.relation.code, resolveName(r.target, null, 0)))
          }
          rels
        }

        // delete stale relations and note which desired relations are still there
        def flip[A,B,C] (t3 :Fun.Tuple3[A,B,C]) = Fun.t3(t3.c, t3.b, t3.a)
        val iter = _relsFrom.tailSet(Fun.t3(defId, null, null :Id)).iterator
        var cont = true ; while (cont && iter.hasNext) {
          val t3 = iter.next
          if (t3.a != defId) cont = false // we're done here
          else if (!rels.remove(t3)) {
            iter.remove()
            _relsTo.remove(flip(t3))
          }
        }
        // now insert any new relations into relsFrom and relsTo
        if (!rels.isEmpty) {
          _relsFrom.addAll(rels)
          rels foreach { t3 => _relsTo.add(flip(t3)) }
        }
      }

      // then go through and store additional data like sigs, docs and uses now that all the
      // defs are stored and IDed, we can resolve many use refs to more compact local refs
      def storeDatas (defs :JIterable[DefInfo]) {
        if (defs != null) defs foreach { df =>
          storeData(df)
          storeDatas(df.defs)
        }
      }
      storeDatas(topDef.defs)

      // accumulate to the indices for refs made in this compunit
      extRefs foreach { refId =>
        Mutable.getOrPut(_nUseBySrc, refId, Set.builder[Integer]()) += unitId
      }

      // filter the reused source ids from the old source ids and delete any that remain
      val staleIds = oldSourceIds -- newSourceIds
      if (!staleIds.isEmpty()) removeDefs(staleIds)
      _srcDefs.put(unitId, newSourceIds)
      _srcInfo.put(unitId, SourceInfo(srcKey, indexed))

      _writeCount += 1
      if (_writeCount > COMMIT_EVERY) {
        _db.commit()
        _writeCount = 0
      }

      // System.err.println(srcKey + " has " + newSourceIds.size() + " defs")
    }
  }

  override def clear () {
    _names.clear()
    _fqNames.clear()
    _srcToId.clear()
    _srcInfo.clear()
    _srcDefs.clear()
    _topDefs.clear()
    _defs.clear()
    _defMems.clear()
    _defUses.clear()
    _defSig.clear()
    _defDoc.clear()
    _indices.values.foreach { _.clear() }
    _relsFrom.clear()
    _relsTo.clear()
  }

  override def close () {
    _db.close()
  }

  override def topLevelDefs = _topDefs map _defs.get

  override def lastIndexed (source :Source) =
    Option(_srcToId.get(source.toString)).map(_srcInfo.get).map(_.indexed) getOrElse 0L

  override def sourceDefs (source :Source) = {
    val unitId = _srcToId.get(source.toString())
    if (unitId == null) throw new IllegalArgumentException("Unknown source " + source)
    _srcDefs.get(unitId) map _defs.get
  }

  override def `def` (defId :Id) = reqdef(defId, _defs.get(defId))
  override def `def` (ref :Ref.Global) = _fqNames.get(ref.toString) match {
    case null => Optional.empty()
    case nmid => Optional.ofNullable(_defs.get(toDefId(nmid, _names.get(nmid).unitId)))
  }
  override def ref (defId :Id) = globalRef(toNameId(defId))

  override def defsIn (defId :Id) = _defMems.getOrDefault(defId, NoIds) map _defs.get

  override def usesIn (defId :Id) = resolveUses(defUses(defId))
  private def defUses (defId :Id) = _defUses.getOrDefault(defId, Seq())

  override def relationsFrom (rel :Relation, defId :Id) = {
    val rels = new HashSet[Ref]()
    val iter = _relsFrom.tailSet(Fun.t3(defId, rel.code, null :Id)).iterator
    var cont = true ; while (cont && iter.hasNext) {
      val t3 = iter.next
      if (t3.a != defId || t3.b != rel.code) cont = false
      else rels.add(nameToRef(t3.c))
    }
    rels
  }

  override def relationsTo (rel :Relation, ref :Ref) = {
    val nameId = ref match {
      case loc :Ref.Local  => toNameId(loc.defId)
      case glo :Ref.Global => lookupName(glo)
    }
    val defs = new HashSet[Def]()
    val iter = _relsTo.tailSet(Fun.t3(nameId, rel.code, null :Id)).iterator
    var cont = true ; while (cont && iter.hasNext) {
      val t3 = iter.next
      if (t3.a != nameId || t3.b != rel.code) cont = false
      else defs.add(`def`(t3.c))
    }
    defs
  }

  override def usesOf (df :Def) = {
    val isLocal = (df.project == this)
    val nameId = if (isLocal) toNameId(df.id) else lookupName(df.globalRef)
    // if we don't know about this global name, then we have no uses of it
    if (nameId == null) Collections.emptyMap[Source,Array[Int]]
    else {
      val uses = new HashMap[Source,Array[Int]]()
      def addUses (unitId :Integer) {
        val info = _srcInfo.get(unitId)
        if (info == null) {
          println(s"Def reports use in non-existent source [def=$df, unitId=$unitId]")
        } else {
          // right now we brute force our way through all uses in matching comp units if that turns
          // out to be too expensive, we can include enclosing def ids in our index
          val offsets = Array.newBuilder[Int]
          for (defId <- _srcDefs.get(unitId);
               use  <- defUses(defId)) if (use.nameId == nameId) offsets += use.offset
          uses.put(Source.fromString(info.source), offsets.result)
        }
      }
      if (isLocal) addUses(toUnitId(df.id))
      _useBySrc.getOrDefault(nameId, Set()) foreach addUses
      uses
    }
  }

  override def sig (defId :Id) = _defSig.get(defId) match {
    case null => Optional.empty[Sig]
    case psig => Optional.of(psig.toSig(this))
  }
  override def doc (defId :Id) = _defDoc.get(defId) match {
    case null => Optional.empty[Doc]
    case pdoc => Optional.of(pdoc.toDoc(this))
  }

  override def source (defId :Id) = {
    val info = _srcInfo.get(toUnitId(defId))
    if (info == null) throw new IllegalArgumentException("No source for def " + idToString(defId))
    Source.fromString(info.source)
  }

  override def idToString (id :Id) = {
    val unitId = toUnitId(id)
    val defId = id >> UNIT_BITS
    s"$unitId:$defId"
  }

  override def find (query :Query, expOnly :Boolean, into :JList[Def]) {
    val pre = query.prefix
    val name = query.name
    val lowKey = Fun.t2(name, null :Id)
    query.kinds foreach { kind =>
      def loop (iter :JIterator[Fun.Tuple2[String,Id]]) {
        if (iter.hasNext) {
          val ent = iter.next
          if (!(pre && !ent.a.startsWith(name)) && !(!pre && !ent.a.equals(name))) {
            val df = _defs.get(ent.b)
            if (df != null) { // index can contain stale entries
              // TODO: validate that def matches query (index may have stale link to reused def id)
              if (!expOnly || df.exported) into.add(df)
            }
            loop(iter)
          }
        }
      }
      loop(_indices(kind).tailSet(lowKey).iterator)
    }
  }

  private final val UNIT_BITS = 16
  private final val UNIT_SKIP = (1 << UNIT_BITS) // 65536
  private final val UNIT_MASK = UNIT_SKIP-1      // 0xFFFF

  @inline private def toNameId (defId :Id) :Id  = defId & ~UNIT_MASK
  @inline private def toUnitId (defId :Id) :Int = (defId & UNIT_MASK).toInt
  @inline private def toDefId (nameId :Id, unitId :Int) = nameId | unitId

  private def globalRef (nameId :Id) :Ref.Global =
    if (nameId == ZeroId) Ref.Global.ROOT
    else _names.get(nameId) match {
      case null => throw new IllegalStateException(s"Missing name: $nameId")
      case name => globalRef(name.parentId).plus(name.id)
    }

  private def nameToRef (nameId :Id) :Ref = nameToRef(nameId, _names.get(nameId))
  private def nameToRef (nameId :Id, name :Name) :Ref =
    if (name.unitId == 0) globalRef(name.parentId).plus(name.id)
    else Ref.local(this, toDefId(nameId, name.unitId))

  private def lookupName (ref :Ref.Global) :Id = _fqNames.get(ref.toString)

  private def resolveName (ref :Ref.Global, kind :Kind, unitId :Int) :Id = {
    val fqKey = ref.toString
    val nmid = _fqNames.get(fqKey)
    if (nmid == null) {
      val parentId = if (ref.parent == Ref.Global.ROOT) ZeroId
                     else resolveName(ref.parent, null, 0)
      val nameId = _maxNameId.addAndGet(UNIT_SKIP)
      val name = Name(ref.id, parentId, kind, unitId)
      _names.put(nameId, name)
      _fqNames.put(fqKey, nameId)
      nameId
    } else {
      // if we were supplied with a unitId or kind, make sure they're correct
      if (unitId > 0 || kind != null) {
        val name = _names.get(nmid)
        // this logic is a little twisty
        if (unitId > 0) {
          // either we're resolving a def, in which case we have a unitId and kind
          if (name.unitId != unitId) _names.put(nmid, name.copy(kind=kind, unitId=unitId))
        } else if (kind != null) {
          // or we're resolving a use, and just have a kind (but don't want to overwrite unitId)
          if (name.kind != kind) _names.put(nmid, name.copy(kind=kind))
        }
      }
      nmid
    }
  }

  private[store] def resolveUses (puses :Seq[PUse]) :JList[Use] = {
    val uses = new ArrayList[Use](puses.size)
    puses foreach { use =>
      val name = _names.get(use.nameId)
      uses.add(new Use(nameToRef(use.nameId, name), name.kind, use.offset, use.length))
    }
    uses
  }

  private def removeDefs (defIds :IdSet) {
    println(s"Removing ${defIds.size} defs.")
    // we want to remove defs from highest def id to lowest,
    // so that we're sure to remove children before parents
    Seq.builder[Id].append(defIds).build().reverse foreach { defId =>
      // remove the def from the myriad def maps
      _topDefs.remove(defId)
      val df = _defs.remove(defId)
      _defMems.remove(defId)
      _defUses.remove(defId)
      _defSig.remove(defId)
      _defDoc.remove(defId)
      // remove any uses record for the def
      val nameId = toNameId(defId)
      _useBySrc.remove(nameId)
      // remove the def's name from the name tables and by-name indices
      val name = _names.remove(nameId)
      if (name == null) println(s"No name for ${idToString(defId)} / $df")
      else try {
        _indices(name.kind).remove(Fun.t2(name.id.toLowerCase, defId))
        // do this last because it could choke if somehow a name was missing up the chain
        val ref = globalRef(name.parentId).plus(name.id)
        _fqNames.remove(ref.toString)
      } catch {
        case t :Throwable => println(s"Remove name choked ${idToString(defId)} / $df / $name: $t")
      }
    }
  }

  private def reqdef[T] (defId :Id, value :T) = {
    if (value == null) throw new NoSuchElementException(s"No def with id ${idToString(defId)}")
    value
  }
}

object MapDBStore {

  def checkSchema (storePath :Path) :Path = {
    // check our schema version and blow away the old db if the schema is out of date since a store
    // is basically a fancy cache, it'll be rebuilt
    val versFile = Paths.get(storePath.toString+".v")
    var fileVers = 0
    try {
      if (Files.exists(versFile)) {
        fileVers = Integer.parseInt(Files.readAllLines(versFile).get(0))
      }
    } catch {
      case t :Throwable =>
        println(s"Error reading version from $versFile")
        t.printStackTrace(System.err)
    }
    if (fileVers < SCHEMA_VERS) {
      val storeName = storePath.getFileName.toString
      assert(storeName.length > 0)
      try {
        val parent = storePath.getParent
        if (parent != null && Files.exists(parent)) Files.list(parent).collect(Collectors.toList[Path]) foreach { path =>
          if (path.getFileName.toString.startsWith(storeName)) Files.delete(path)
        }
      } catch {
        case ioe :IOException => println(s"Error deleting stale database: $ioe")
      }
      try {
        Files.write(versFile, Arrays.asList(String.valueOf(SCHEMA_VERS)))
      } catch {
        case ioe :IOException => println(s"Error writing version file $versFile: $ioe")
      }
    }
    storePath
  }

  private final val SCHEMA_VERS = 3
}
