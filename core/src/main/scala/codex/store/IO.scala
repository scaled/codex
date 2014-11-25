//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.store

import codex.model._
import java.io.{DataInput, DataOutput, Externalizable, ObjectInput, ObjectOutput, Serializable}
import java.util.{ArrayList, List}
import org.mapdb.Serializer
import scala.collection.immutable.TreeSet
import scaled.{Seq, Set}

object IO {

  type Id = java.lang.Long
  type IdSet = TreeSet[Id]
  type IdSetBuilder = scala.collection.mutable.Builder[Id,TreeSet[Id]]

  val ZeroId :Id = 0L
  def NoIds :IdSet = TreeSet[Id]()
  def idSetBuilder :IdSetBuilder = TreeSet.newBuilder[Id]

  case class SourceInfo (source :String, indexed :Long)
  case class Name (id :String, parentId :Long, kind :Kind, unitId :Int)
  case class PUse (nameId :Long, offset :Int, length :Int)
  case class PSig (text :String, uses :Seq[PUse]) {
    def toSig (store :MapDBStore) = new Sig(text, store.resolveUses(uses))
  }
  case class PDoc (offset :Int, length :Int, uses :Seq[PUse]) {
    def toDoc (store :MapDBStore) = new Doc(offset, length, store.resolveUses(uses))
  }

  class SourceInfoSerializer extends Serializer[SourceInfo] with Serializable {
    override def fixedSize = -1
    override def serialize (out :DataOutput, info :SourceInfo) {
      out.writeUTF(info.source)
      out.writeLong(info.indexed)
    }
    override def deserialize (in :DataInput, available :Int) =
      new SourceInfo(in.readUTF, in.readLong)
  }
  val SRCINFO_SZ = new SourceInfoSerializer()

  class IdSetSerializer extends Serializer[IdSet] with Serializable {
    override def fixedSize = -1
    override def serialize (out :DataOutput, ids :IdSet) {
      out.writeInt(ids.size)
      ids foreach { out.writeLong(_) }
    }
    override def deserialize (in :DataInput, available :Int) = {
      val sb = idSetBuilder
      val count = in.readInt
      sb.sizeHint(count)
      var ii = 0 ; while (ii < count) { sb += in.readLong ; ii += 1 }
      sb.result
    }
  }
  val IDS_SZ = new IdSetSerializer()

  class IntSetSerializer extends Serializer[Set[Integer]] with Serializable {
    override def fixedSize = -1
    override def serialize (out :DataOutput, ids :Set[Integer]) {
      out.writeInt(ids.size)
      ids foreach { out.writeInt(_) }
    }
    override def deserialize (in :DataInput, available :Int) = {
      val count = in.readInt
      val sb = Set.builder[Integer](count)
      var ii = 0 ; while (ii < count) { sb += in.readInt ; ii += 1 }
      sb.build()
    }
  }
  val INT_SET_SZ = new IntSetSerializer()

  class NameSerializer extends Serializer[Name] with Serializable {
    override def fixedSize = -1
    override def serialize (out :DataOutput, name :Name) {
      out.writeUTF(name.id)
      out.writeLong(name.parentId)
      writeEnum(out, name.kind)
      out.writeInt(name.unitId)
    }
    override def deserialize (in :DataInput, available :Int) =
      Name(in.readUTF(), in.readLong(), readEnum(classOf[Kind], in), in.readInt())
  }
  val NAME_SZ = new NameSerializer()

  class SigSerializer extends Serializer[PSig] with Serializable {
    override def fixedSize = -1
    override def serialize (out :DataOutput, sig :PSig) {
      out.writeUTF(sig.text)
      writeUses(out, sig.uses)
    }
    override def deserialize (in :DataInput, available :Int) = PSig(in.readUTF, readUses(in))
  }
  val SIG_SZ = new SigSerializer()

  class DocSerializer extends Serializer[PDoc] with Serializable {
    override def fixedSize = -1
    override def serialize (out :DataOutput, doc :PDoc) {
      out.writeInt(doc.offset)
      out.writeInt(doc.length)
      writeUses(out, doc.uses)
    }
    override def deserialize (in :DataInput, available :Int) =
      PDoc(in.readInt, in.readInt, readUses(in))
  }
  val DOC_SZ = new DocSerializer()

  class UsesSerializer extends Serializer[Seq[PUse]] with Serializable {
    override def fixedSize = -1
    override def serialize (out :DataOutput, uses :Seq[PUse]) = writeUses(out, uses)
    override def deserialize (in :DataInput, available :Int) = readUses(in)
  }
  val USES_SZ = new UsesSerializer()

  // StoreSerializers rely on the static store field being initialized at the right time this
  // hackery is due to MapDB's requirement that serializers be themselves serialized (via Java
  // serialization) and stored in the database they're used with needless PITA
  var store :ProjectStore = _
  class StoreSerializer extends Externalizable {
    @transient val store = IO.store ; {
      if (this.store == null) throw new IllegalStateException(
        "IO.store must be set prior to instantiating serializers")
    }
    override def writeExternal (out :ObjectOutput) {}
    override def readExternal (in :ObjectInput) {}
  }

  class DefSerializer extends StoreSerializer with Serializer[Def] {
    override def fixedSize = -1
    override def serialize (out :DataOutput, df :Def) = writeDef(out, df)
    override def deserialize (in :DataInput, available :Int) = readDef(in, store)
  }

  def readDef (in :DataInput, store :ProjectStore) = new Def(
    store, in.readLong /*id*/, zero2null(in.readLong) /*outerId*/,
    readEnum(classOf[Kind], in), readEnum(classOf[Flavor], in),
    in.readBoolean /*exported*/, readEnum(classOf[Access], in),
    in.readUTF /*name*/, in.readInt /*offset*/,
    in.readInt /*bodyStart*/, in.readInt /*bodyEnd*/)

  def writeDef (out  :DataOutput, df :Def) {
    out.writeLong(df.id)
    out.writeLong(null2zero(df.outerId))
    writeEnum(out, df.kind)
    writeEnum(out, df.flavor)
    out.writeBoolean(df.exported)
    writeEnum(out, df.access)
    out.writeUTF(df.name)
    out.writeInt(df.offset)
    out.writeInt(df.bodyStart)
    out.writeInt(df.bodyEnd)
  }

  def readUse (in :DataInput) = PUse(in.readLong(), in.readInt(), in.readInt())
  def writeUse (out :DataOutput, use :PUse) {
    out.writeLong(use.nameId)
    out.writeInt(use.offset)
    out.writeInt(use.length)
  }

  def readUses (in :DataInput) :Seq[PUse] = {
    val count = in.readInt
    val ub = Seq.builder[PUse](count)
    var ii = 0 ; while (ii < count) { ub += readUse(in) ; ii += 1 }
    ub.build()
  }
  def writeUses (out :DataOutput, uses :Seq[PUse]) {
    out.writeInt(uses.size)
    var iter = uses.iterator ; while (iter.hasNext) writeUse(out, iter.next)
  }

  def readEnum[E <: Enum[E]] (eclass :Class[E], in :DataInput) = {
    val estr = in.readUTF
    if (estr.length == 0) null.asInstanceOf[E] else Enum.valueOf(eclass, estr)
  }
  def writeEnum (out :DataOutput, eval :Enum[_]) =
    out.writeUTF(if (eval == null) "" else eval.name)

  @inline private def zero2null (value :Long) = if (value == 0L) null else (value :Id)
  @inline private def null2zero (value :Id) = if (value == null) 0L else value.longValue
}
