//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.store;

import codex.model.*;
import com.google.common.collect.Lists;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * Inflates bytes into objects.
 */
public class Inflater extends Flater {

  public Inflater (byte[] data) {
    _data = data;
    _buf = ByteBuffer.wrap(data);
  }

  public boolean getBoolean () {
    return _buf.get() == TRUE;
  }

  public int getInt () {
    return _buf.getInt();
  }

  public String getString () {
    int length = getInt();
    return fromBytes(_data, _buf.position(), length);
  }

  public Def getDef () {
    return new Def(getInt() /*projectId*/, getInt() /*defId*/, getInt() /*outerId*/,
                   Kind.valueOf(getString()), getBoolean() /*exported*/,
                   getString() /*name*/, getInt() /*offset*/);
  }

  public List<Def> getDefs () {
    int defCount = getInt();
    List<Def> defs = Lists.newArrayListWithExpectedSize(defCount);
    for (int ii = 0; ii < defCount; ii++) defs.add(getDef());
    return defs;
  }

  public Ref getRef () {
    boolean local = getBoolean();
    if (local) return Ref.local(getInt() /*projectId*/, getInt() /*defId*/);
    else return Ref.Global.fromString(getString());
  }

  public Use getUse () {
    return new Use(getRef(), Kind.valueOf(getString()), getInt() /*offset*/, getInt() /*length*/);
  }

  public List<Use> getUses () {
    int useCount = getInt();
    List<Use> uses = Lists.newArrayListWithExpectedSize(useCount);
    for (int ii = 0; ii < useCount; ii++) uses.add(getUse());
    return uses;
  }

  public Sig getSig () {
    return new Sig(getString() /*text*/, getDefs(), getUses());
  }

  public Doc getDoc () {
    return new Doc(getInt() /*offset*/, getInt() /*length*/, getUses());
  }

  private final byte[] _data;
  private final ByteBuffer _buf;
}
