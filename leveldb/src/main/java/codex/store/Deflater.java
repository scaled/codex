//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.store;

import codex.model.*;
import java.nio.ByteBuffer;
import java.util.List;

public class Deflater extends Flater {

  public Deflater () {
    this(128);
  }

  public Deflater (int expectedSize) {
    _data = new byte[expectedSize];
    _buf = ByteBuffer.wrap(_data);
  }

  public byte[] bytes () {
    return _data;
  }

  public Deflater putBoolean (boolean value) {
    prepPut(1).put(value ? TRUE : FALSE);
    return this;
  }

  public Deflater putInt (int value) {
    prepPut(4).putInt(value);
    return this;
  }

  public Deflater putString (String value) {
    byte[] data = toBytes(value);
    prepPut(data.length+4).putInt(data.length).put(data);
    return this;
  }

  public Deflater putDef (Def def) {
    byte[] kind = toBytes(def.kind.name());
    byte[] name = toBytes(def.name);
    int size = (4 /*projectId*/ + 4 /*id*/ + 4 /*outerId*/ + 4+kind.length + 1 /*exported*/ +
                4+name.length + 4 /*offset*/);
    prepPut(size).putInt(def.projectId).putInt(def.id).putInt(def.outerId).putInt(kind.length).
      put(kind).put(def.exported ? TRUE : FALSE).putInt(name.length).put(name).putInt(def.offset);
    return this;
  }

  public Deflater putDefs (List<Def> defs) {
    putInt(defs.size());
    for (Def def : defs) putDef(def);
    return this;
  }

  public Deflater putRef (Ref ref) {
    if (ref instanceof Ref.Local) {
      putBoolean(true);
      putInt(((Ref.Local)ref).projectId);
      putInt(((Ref.Local)ref).defId);
    } else {
      putBoolean(false);
      putString(ref.toString());
    }
    return this;
  }

  public Deflater putUse (Use use) {
    putRef(use.ref);
    byte[] refKind = toBytes(use.refKind.name());
    int size = (4+refKind.length + 4 /*offset*/ + 4 /*length*/);
    prepPut(size).putInt(refKind.length).put(refKind).putInt(use.offset).putInt(use.length);
    return this;
  }

  // public List<Use> getUses () {
  //   int useCount = getInt();
  //   List<Use> uses = Lists.newArrayListWithExpectedSize(useCount);
  //   for (int ii = 0; ii < useCount; ii++) uses.add(getUse());
  //   return uses;
  // }

  // public Sig getSig () {
  //   return new Sig(getString() /*text*/, getDefs(), getUses());
  // }

  // public Doc getDoc () {
  //   return new Doc(getInt() /*offset*/, getInt() /*length*/, getUses());
  // }

  protected ByteBuffer prepPut (int bytes) {
    int pos = _buf.position();
    int need = pos + bytes;
    if (need <= _data.length) return _buf;
    else {
      int nsize = ((need >> 7) + 1) << 7 + 128; // round up to mult of 128 then add 128
      byte[] ndata = new byte[nsize];
      System.arraycopy(_data, 0, ndata, 0, _data.length);
      ByteBuffer buf = ByteBuffer.wrap(ndata);
      buf.position(pos);
      _data = ndata;
      _buf = buf;
      return buf;
    }
  }

  private byte[] _data;
  private ByteBuffer _buf;
}
