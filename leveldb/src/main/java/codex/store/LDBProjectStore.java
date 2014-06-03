//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.store;

import codex.Codex;
import codex.extract.StoreWriter;
import codex.extract.Writer;
import codex.model.*;
import codex.store.IdMap;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import org.iq80.leveldb.*;
import org.iq80.leveldb.impl.Iq80DBFactory;

public class LDBProjectStore extends ProjectStore {

  /** Various metadata. */
  public static final byte METADATA = 1;
  /** Mapping from source (UTF8 string) to unit id (int). */
  public static final byte SOURCE_TO_UNITID = 2;
  /** Mapping from unit id (int) to source (UTF8 string). */
  public static final byte UNITID_TO_SOURCE = 3;
  /** Mapping from unit id (int) to def ids (int vector). */
  public static final byte UNITID_TO_DEFIDS = 4;
  /** Mapping from def id (int) to def (data). */
  public static final byte DEFID_TO_DEF = 5;
  /** Mapping from def id (int) to member def ids (int vector). */
  public static final byte DEFID_TO_MEMDEFS = 6;
  /** Mapping from def id (int) to uses (data vector). */
  public static final byte DEFID_TO_USES = 7;
  /** Mapping from def id (int) to sig (data). */
  public static final byte DEFID_TO_SIG = 8;
  /** Mapping from def id (int) to doc (data). */
  public static final byte DEFID_TO_DOC = 9;

  /** A writer that can be used to write metadata to this store. Handles incremental updates. */
  public Writer writer = new StoreWriter(this) {

    @Override protected int assignUnitId (Source source) {
      return 0; // TODO
    }

    @Override protected int resolveDefId (Ref.Global id) {
      return 0; // TODO
    }

    @Override protected void storeDef (Def def) {
      // TODO
    }

    @Override protected void storeUse (int defId, Use use) {
      // TODO
    }

    @Override protected void storeSig (int defId, Sig sig) {
      // TODO
    }

    @Override protected void storeDoc (int defId, Doc doc) {
      // TODO
    }
  };

  public LDBProjectStore (File store) throws IOException {
    _store = store;
    _db = createDB(store);
  }

  /**
   * Wipes the contents of this store and prepares it to be rebuilt.
   */
  public void clear () throws IOException {
    _db.close();
    Iq80DBFactory.factory.destroy(_store, new Options());
    _db = createDB(_store);
  }

  public int putUnit (Source source) {
    byte[] path = Flater.toBytes(source.toString());

    // check whether we have a unit id assigned to this path
    byte[] id = _db.get(path);

    return 0; // TODO
  }

  public void putUnitDefs (int unitId, Set<Integer> defIds) {
    Deflater data = new Deflater(defIds.size()*4);
    for (int id : defIds) data.putInt(id);
    _db.put(idKey(UNITID_TO_DEFIDS, unitId), data.bytes());
  }

  @Override public Iterable<Def> topLevelDefs () {
    return null; // TODO
  }

  @Override public boolean isIndexed (Source source) {
    return false; // TODO
  }

  @Override public Iterable<Def> sourceDefs (Source source) {
    return null; // TODO
  }

  @Override public Optional<Def> def (Ref.Global ref) {
    return Optional.empty(); // TODO
  }

  @Override public Def def (int defId) {
    return reqById(DEFID_TO_DEF, defId).getDef();
  }

  @Override public Ref.Global ref (int defId) {
    return null; // TODO
  }

  @Override public List<Def> memberDefs (int defId) {
    Inflater data = getById(DEFID_TO_MEMDEFS, defId);
    if (data == null) return Collections.emptyList();
    // TODO: test whether this is faster or slower than using DBIterator+seek
    int count = data.getInt();
    List<Def> defs = Lists.newArrayListWithExpectedSize(count);
    for (int ii = 0; ii < count; ii++) defs.add(def(data.getInt()));
    return defs;
  }

  @Override public List<Use> uses (int defId) {
    Inflater data = getById(DEFID_TO_USES, defId);
    return (data == null) ? Collections.emptyList() : data.getUses();
  }

  @Override public Optional<Sig> sig (int defId) {
    return Optional.ofNullable(getById(DEFID_TO_SIG, defId)).map(data -> data.getSig());
  }

  @Override public Optional<Doc> doc (int defId) {
    return Optional.ofNullable(getById(DEFID_TO_DOC, defId)).map(data -> data.getDoc());
  }

  @Override public Source source (int defId) {
    return Source.fromString(reqById(UNITID_TO_SOURCE, IdMap.toUnitId(defId)).getString());
  }

  @Override public void find (Codex.Query query, boolean expOnly, List<Def> into) {
    // TODO
  }

  @Override public void close () throws IOException {
    _db.close();
  }

  private DB createDB (File file) throws IOException {
    Options options = new Options();
    options.createIfMissing(true);
    options.logger(new Logger() {
      public void log (String message) {
        System.out.println(message);
      }
    });
    return Iq80DBFactory.factory.open(file, options);
  }

  private byte[] idKey (byte table, int defId) {
    return _idKey.get().toKey(table, defId);
  }

  private Inflater getById (byte table, int defId) {
    byte[] data = _db.get(idKey(table, defId));
    return (data == null) ? null : new Inflater(this, data);
  }

  private Inflater reqById (byte table, int defId) {
    Inflater data = getById(table, defId);
    if (data == null) throw new NoSuchElementException("No def with id " + defId);
    return data;
  }

  private final File _store;
  private final ReadOptions _ropts = new ReadOptions();
  private DB _db;

  private class IdKey {
    public byte[] toKey (byte mapping, int id) {
      _buf.position(0);
      _buf.put(mapping);
      _buf.putInt(id);
      return _key;
    }
    private final byte[] _key = new byte[5];
    private final ByteBuffer _buf = ByteBuffer.wrap(_key);
  }
  private final ThreadLocal<IdKey> _idKey = new ThreadLocal<IdKey>() {
    @Override protected IdKey initialValue () { return new IdKey(); }
  };
}
