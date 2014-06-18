//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.store;

import codex.model.*;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.mapdb.Serializer;

public class IO implements Serializable {

  public static ProjectStore store;

  public static class StoreSerializer implements Serializable {
    public transient ProjectStore store;
    private void readObject (ObjectInputStream in) {
      this.store = IO.store;
      if (this.store == null) throw new IllegalStateException(
        "IO.store must be set prior to unserializing serializers");
    }
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
  public static final Serializer<Def> defSz = new DefSerializer();

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
  public static final Serializer<Sig> sigSz = new SigSerializer();

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
  public static final Serializer<Doc> docSz = new DocSerializer();

  public static class IdsSerializer implements Serializer<Set<Long>>, Serializable {
    @Override public int fixedSize() { return -1; }
    @Override public void serialize (DataOutput out, Set<Long> ids) throws IOException {
      out.writeInt(ids.size());
      for (Long id : ids) out.writeLong(id);
    }
    @Override public Set<Long> deserialize (DataInput in, int available) throws IOException {
      int count = in.readInt();
      Set<Long> ids = new HashSet<>(count);
      for (int ii = 0; ii < count; ii++) ids.add(in.readLong());
      return ids;
    }
  };
  public static final Serializer<Set<Long>> idsSz = new IdsSerializer();

  public static class UsesSerializer extends StoreSerializer implements Serializer<List<Use>> {
    @Override public int fixedSize() { return -1; }
    @Override public void serialize (DataOutput out, List<Use> uses) throws IOException {
      writeUses(out, uses);
    }
    @Override public List<Use> deserialize (DataInput in, int available) throws IOException {
      return readUses(in, store);
    }
  };
  public static final Serializer<List<Use>> usesSz = new UsesSerializer();

  public static Def readDef (DataInput in, ProjectStore store) throws IOException {
    return new Def(store, in.readLong() /*id*/, zero2null(in.readLong()) /*outerId*/,
                   readKind(in) /*kind*/, in.readBoolean() /*exported*/, in.readUTF() /*name*/,
                   in.readInt() /*offset*/);
  }
  public static void writeDef (DataOutput out, Def def) throws IOException {
    out.writeLong(def.id);
    out.writeLong(null2zero(def.outerId));
    writeKind(out, def.kind);
    out.writeBoolean(def.exported);
    out.writeUTF(def.name);
    out.writeInt(def.offset);
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
    return new Use(readRef(in, store), readKind(in),
                   in.readInt() /*offset*/, in.readInt() /*length*/);
  }
  public static void writeUse (DataOutput out, Use use) throws IOException {
    writeRef(out, use.ref);
    writeKind(out, use.refKind);
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

  public static Kind readKind (DataInput in) throws IOException {
    return Enum.valueOf(Kind.class, in.readUTF());
  }
  public static void writeKind (DataOutput out, Kind kind) throws IOException {
    out.writeUTF(kind.name());
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
