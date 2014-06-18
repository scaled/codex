//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.store;

import codex.model.Ref;
import com.google.common.base.Preconditions;
import java.util.HashSet;
import java.util.Set;

/**
 * Aids with assigning ids to defs. Also helps with the incremental update of a set of defs,
 * preserving ids for retained defs and tracking defs that no longer exist.
 */
public class IdMap {

  /** Each compilation unit is limited to a max number of defs. */
  public static final int DEFS_PER_UNIT = 32768;

  /** Extracts the unit id from {@code defId}. */
  public static long toUnitId (long defId) {
    return defId / DEFS_PER_UNIT;
  }

  /** The id of the unit for which this map provides def ids. */
  public final int unitId;

  /** Creates a map which will assign def ids for the comp unit identified by {@code unitId}.
    * @param tree the ref tree in which exported refs are resolved. */
  public IdMap (RefTree tree, int unitId) {
    Preconditions.checkArgument(unitId != 0, "Unit id must be non-zero");
    this.unitId = unitId;
    _tree = tree;
    _nextDefId = (long)(unitId * DEFS_PER_UNIT);
  }

  /** Resolves the supplied ref, assigning it a new id in this compilation unit, if needed.
    * @param exported whether or not the ref is visible beyond this compilation unit. */
  public synchronized Long resolve (Ref.Global ref) {
    Long assignId = _nextDefId;
    Long id = _tree.resolve(ref, assignId);
    if (id.equals(assignId)) {
      _ids.add(id);
      _nextDefId += 1;
    }
    _toPurge.remove(id);
    return id;
  }

  /** Returns a copy of all ref ids assigned for this compilation unit. */
  public synchronized Set<Long> copyIds () {
    return new HashSet<>(_ids);
  }

  /** Snapshots the current id set in preparation for reindexing our compilation unit. Should be
    * followed by a call to {@link #purge} after the unit is reindexed. */
  public synchronized void snapshot () {
    _toPurge = copyIds();
  }

  /** Purges any ids that were not re-resolved since the last call to {@link #snapshot}.
    * @return the set of purged ids. */
  public synchronized Set<Long> purge () {
    Set<Long> toPurge = _toPurge;
    _toPurge = null;
    _ids.removeAll(toPurge);
    return toPurge;
  }

  private final RefTree _tree;
  private final Set<Long> _ids = new HashSet<>();
  private Set<Long> _toPurge = new HashSet<>();
  private Long _nextDefId;
}
