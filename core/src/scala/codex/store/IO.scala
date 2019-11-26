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
  case class PDef (id :Long, outerId :Long, kind :Kind, flavor :Flavor, exported :Boolean,
                   access :Access, name :String, offset :Int, bodyStart :Int, bodyEnd :Int) {
    def toDef (store :MapDBStore) = new Def(store, id, if (outerId == 0L) null else outerId,
                                            kind, flavor, exported, access, name,
                                            offset, bodyStart, bodyEnd)
  }
  object PDef {
    def apply (df :Def) :PDef = PDef(df.id, if (df.outerId == null) 0L else df.outerId,
                                     df.kind, df.flavor, df.exported, df.access, df.name,
                                     df.offset, df.bodyStart, df.bodyEnd)
  }

  class SourceInfoSerializer extends Serializer[SourceInfo] with Serializable {
    override def fixedSize = -1
    override def serialize (out :DataOutput, info :SourceInfo) :Unit = {
      out.writeUTF(info.source)
      out.writeLong(info.indexed)
    }
    override def deserialize (in :DataInput, available :Int) =
      new SourceInfo(in.readUTF, in.readLong)
  }
  val SRCINFO_SZ = new SourceInfoSerializer()

  class IdSetSerializer extends Serializer[IdSet] with Serializable {
    override def fixedSize = -1
    override def serialize (out :DataOutput, ids :IdSet) :Unit = {
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
    override def serialize (out :DataOutput, ids :Set[Integer]) :Unit = {
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
    override def serialize (out :DataOutput, name :Name) :Unit = {
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
    override def serialize (out :DataOutput, sig :PSig) :Unit = {
      out.writeUTF(sig.text)
      writeUses(out, sig.uses)
    }
    override def deserialize (in :DataInput, available :Int) = PSig(in.readUTF, readUses(in))
  }
  val SIG_SZ = new SigSerializer()

  class DocSerializer extends Serializer[PDoc] with Serializable {
    override def fixedSize = -1
    override def serialize (out :DataOutput, doc :PDoc) :Unit = {
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

  class DefSerializer extends Serializer[PDef] with Serializable {
    override def fixedSize = -1
    override def serialize (out :DataOutput, df :PDef) = writeDef(out, df)
    override def deserialize (in :DataInput, available :Int) = readDef(in)
  }
  val DEF_SZ = new DefSerializer()

  def readDef (in :DataInput) = PDef(
    in.readLong /*id*/, zero2null(in.readLong) /*outerId*/,
    readEnum(classOf[Kind], in), readEnum(classOf[Flavor], in),
    in.readBoolean /*exported*/, readEnum(classOf[Access], in),
    in.readUTF /*name*/, in.readInt /*offset*/,
    in.readInt /*bodyStart*/, in.readInt /*bodyEnd*/)

  def writeDef (out :DataOutput, df :PDef) :Unit = {
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
  def writeUse (out :DataOutput, use :PUse) :Unit = {
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
  def writeUses (out :DataOutput, uses :Seq[PUse]) :Unit = {
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
