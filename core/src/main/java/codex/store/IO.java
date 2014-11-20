//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.store;

import codex.model.*;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.mapdb.Serializer;

public class IO {

  public static class SourceInfo {
    public final String source;
    public final long indexed;

    public SourceInfo (String source, long indexed) {
      this.source = source;
      this.indexed = indexed;
    }
  }

  public static class SourceInfoSerializer implements Serializer<SourceInfo>, Serializable {
    @Override public int fixedSize() { return -1; }
    @Override public void serialize (DataOutput out, SourceInfo info) throws IOException {
      out.writeUTF(info.source);
      out.writeLong(info.indexed);
    }
    @Override public SourceInfo deserialize (DataInput in, int available) throws IOException {
      return new SourceInfo(in.readUTF(), in.readLong());
    }
  }
  public static final Serializer<SourceInfo> SRCINFO_SZ = new SourceInfoSerializer();

  public static class IdsSerializer implements Serializer<IdSet>, Serializable {
    @Override public int fixedSize() { return -1; }
    @Override public void serialize (DataOutput out, IdSet ids) throws IOException {
      out.writeInt(ids.size());
      for (long id : ids.elems) out.writeLong(id);
    }
    @Override public IdSet deserialize (DataInput in, int available) throws IOException {
      long[] elems = new long[in.readInt()];
      for (int ii = 0; ii < elems.length; ii++) elems[ii] = in.readLong();
      return IdSet.of(elems);
    }
  };
  public static final Serializer<IdSet> IDS_SZ = new IdsSerializer();

  public static ProjectStore store;

  // StoreSerialiezers rely on the static store field being initialized at the right time;
  // this hackery is due to MapDB's requirement that serializers be themselves serialized
  // (via Java serialization) and stored in the database they're used with; needless PITA
  public static class StoreSerializer implements Externalizable {
    public transient ProjectStore store = IO.store; {
      if (this.store == null) throw new IllegalStateException(
        "IO.store must be set prior to instantiating serializers");
    }
    public void writeExternal (ObjectOutput out) throws IOException {}
    public void readExternal (ObjectInput in) throws IOException, ClassNotFoundException {}
  }

  public static class DefSerializer extends StoreSerializer implements Serializer<Def> {
    @Override public int fixedSize() { return -1; }
    @Override public void serialize (DataOutput out, Def def) throws IOException {
      writeDef(out, def);
    }
    @Override public Def deserialize (DataInput in, int available) throws IOException {
      return readDef(in, store);
    }
  }

  public static class SigSerializer extends StoreSerializer implements Serializer<Sig> {
    @Override public int fixedSize() { return -1; }
    @Override public void serialize (DataOutput out, Sig sig) throws IOException {
      out.writeUTF(sig.text);
      writeDefs(out, sig.defs);
      writeUses(out, sig.uses);
    }
    @Override public Sig deserialize (DataInput in, int available) throws IOException {
      return new Sig(in.readUTF(), readDefs(in, store), readUses(in, store));
    }
  }

  public static class DocSerializer extends StoreSerializer implements Serializer<Doc> {
    @Override public int fixedSize() { return -1; }
    @Override public void serialize (DataOutput out, Doc doc) throws IOException {
      out.writeInt(doc.offset);
      out.writeInt(doc.length);
      writeUses(out, doc.uses);
    }
    @Override public Doc deserialize (DataInput in, int available) throws IOException {
      return new Doc(in.readInt(), in.readInt(), readUses(in, store));
    }
  };

  public static class UsesSerializer extends StoreSerializer implements Serializer<List<Use>> {
    @Override public int fixedSize() { return -1; }
    @Override public void serialize (DataOutput out, List<Use> uses) throws IOException {
      writeUses(out, uses);
    }
    @Override public List<Use> deserialize (DataInput in, int available) throws IOException {
      return readUses(in, store);
    }
  };

  public static Def readDef (DataInput in, ProjectStore store) throws IOException {
    return new Def(store, in.readLong() /*id*/, zero2null(in.readLong()) /*outerId*/,
                   readEnum(Kind.class, in), readEnum(Flavor.class, in),
                   in.readBoolean() /*exported*/, readEnum(Access.class, in),
                   in.readUTF() /*name*/, in.readInt() /*offset*/,
                   in.readInt() /*bodyStart*/, in.readInt() /*bodyEnd*/);
  }
  public static void writeDef (DataOutput out, Def def) throws IOException {
    out.writeLong(def.id);
    out.writeLong(null2zero(def.outerId));
    writeEnum(out, def.kind);
    writeEnum(out, def.flavor);
    out.writeBoolean(def.exported);
    writeEnum(out, def.access);
    out.writeUTF(def.name);
    out.writeInt(def.offset);
    out.writeInt(def.bodyStart);
    out.writeInt(def.bodyEnd);
  }

  public static List<Def> readDefs (DataInput in, ProjectStore store) throws IOException {
    int count = in.readInt();
    List<Def> defs = new ArrayList<>(count);
    for (int ii = 0; ii < count; ii++) defs.add(readDef(in, store));
    return defs;
  }
  public static void writeDefs (DataOutput out, List<Def> defs) throws IOException {
    out.writeInt(defs.size());
    for (Def def : defs) writeDef(out, def);
  }

  public static Use readUse (DataInput in, ProjectStore store) throws IOException {
    return new Use(readRef(in, store), readEnum(Kind.class, in),
                   in.readInt() /*offset*/, in.readInt() /*length*/);
  }
  public static void writeUse (DataOutput out, Use use) throws IOException {
    writeRef(out, use.ref);
    writeEnum(out, use.refKind);
    out.writeInt(use.offset);
    out.writeInt(use.length);
  }

  public static List<Use> readUses (DataInput in, ProjectStore store) throws IOException {
    int count = in.readInt();
    List<Use> uses = new ArrayList<>(count);
    for (int ii = 0; ii < count; ii++) uses.add(readUse(in, store));
    return uses;
  }
  public static void writeUses (DataOutput out, List<Use> uses) throws IOException {
    out.writeInt(uses.size());
    for (Use use : uses) writeUse(out, use);
  }

  public static <E extends Enum<E>> E readEnum (Class<E> eclass, DataInput in) throws IOException {
    return Enum.valueOf(eclass, in.readUTF());
  }
  public static void writeEnum (DataOutput out, Enum<?> eval) throws IOException {
    out.writeUTF(eval.name());
  }

  public static Ref readRef (DataInput in, ProjectStore store) throws IOException {
    if (in.readBoolean()) return Ref.local(store, in.readLong());
    else return Ref.Global.fromString(in.readUTF());
  }
  public static void writeRef (DataOutput out, Ref ref) throws IOException {
    if (ref instanceof Ref.Local) {
      out.writeBoolean(true);
      out.writeLong(((Ref.Local)ref).defId);
    } else {
      out.writeBoolean(false);
      out.writeUTF(ref.toString());
    }
  }

  private static Long zero2null (long value) {
    return (value == 0L) ? null : value;
  }
  private static long null2zero (Long value) {
    return (value == null) ? 0L : value.longValue();
  }
}
