//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.store;

import codex.model.Ref;
import com.carrotsearch.hppc.IntOpenHashSet;
import com.carrotsearch.hppc.IntSet;

/**
 * Aids with assigning ids to defs. Also helps with the incremental update of a set of defs,
 * preserving ids for retained defs and tracking defs that no longer exist.
 */
public class IdMap {

  /** Each compilation unit is limited to a max number of defs. */
  public static final int DEFS_PER_UNIT = 32768;

  /** Extracts the unit id from {@code defId}. */
  public static int toUnitId (int defId) {
    return defId / DEFS_PER_UNIT;
  }

  /** The id of the unit for which this map provides def ids. */
  public final int unitId;

  /** Creates a map which will assign def ids starting from {@code baseId}. */
  public IdMap (RefTree tree, int unitId) {
    if (unitId == 0) throw new IllegalArgumentException("Unit id must be non-zero");
    this.unitId = unitId;
    _tree = tree;
    _nextDefId = unitId * DEFS_PER_UNIT;
  }

  /** Resolves the supplied ref, assigning it a new id in this compilation unit, if needed. */
  public synchronized int resolve (Ref.Global ref) {
    int assignId = _nextDefId;
    int id = _tree.resolve(ref, assignId);
    if (id == assignId) {
      _ids.add(id);
      _nextDefId += 1;
    }
    return id;
  }

  /** Returns a copy of all ref ids assigned for this compilation unit. */
  public IntSet copyIds () {
    return new IntOpenHashSet(_ids);
  }

  private final RefTree _tree;
  private final IntSet _ids = new IntOpenHashSet();
  private int _nextDefId;
}
