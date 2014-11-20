//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.store;

import java.util.AbstractCollection;
import java.util.Arrays;
import java.util.Iterator;

/** Maintains a set of long ids. Is optimized for not using much memory and for immutable adds. */
public class IdSet extends AbstractCollection<Long> {

  /** Used to build id sets moderately efficiently. */
  public static class Builder {
    private long[] _elems;
    private int _idx = 0;

    public Builder () { this(16); }
    public Builder (int expectedSize) { _elems = new long[expectedSize]; }

    public Builder add (long elem) {
      if (_idx == _elems.length) {
        _elems = Arrays.copyOf(_elems, _elems.length*2);
      }
      _elems[_idx++] = elem;
      return this;
    }

    public Builder add (IdSet ids) {
      int nidx = _idx + ids.size();
      if (nidx > _elems.length) {
        int nsize = _elems.length*2;
        while (nidx > nsize) nsize *= 2;
        _elems = Arrays.copyOf(_elems, nsize);
      }
      System.arraycopy(ids.elems, 0, _elems, _idx, ids.elems.length);
      _idx = nidx;
      return this;
    }

    public IdSet build () {
      // sort the array, compact over any duplicates, truncate the array and build
      long[] elems = _elems; int size = _idx;
      if (size == 0) return EMPTY;
      Arrays.sort(elems, 0, size);
      int uu = 1;
      for (int ii = 1; ii < size; ii += 1) {
        long cur = elems[ii]; long prev = elems[ii-1];
        if (cur != prev) {
          if (uu < ii) elems[uu] = cur;
          uu++;
        }
      }
      return new IdSet(Arrays.copyOf(elems, uu));
    }
  }

  /** Creates a set with the specified initial elements (which must be sorted). */
  public static IdSet of (long[] elems) { return new IdSet(elems); }
  /** Returns an id set builder. */
  public static Builder builder () { return new Builder(); }

  /** The empty id set. */
  public static final IdSet EMPTY = new IdSet(new long[0]);

  /** The elements in this set, for fast iteration, don't mutate! */
  public final long[] elems;

  /** Creates an id set with {@code elems}, which must be sorted. */
  public IdSet (long[] elems) { this.elems = elems; }

  /** Returns whether this set is empty. */
  public boolean isEmpty () { return size() == 0; }

  /** Returns the length of this set. */
  public int size () { return elems.length; }
  /** Returns whether this set contains {@code elem}. */
  public boolean contains (long elem) { return Arrays.binarySearch(elems, elem) >= 0; }

  /**
   * Returns a new set containing {@code elem}. If this set already contains {@code elem}, {@code
   * this} will be returned. Note: this only works once. You can't call plus on a set returned by
   * plus. It's meant to augment an int set with a single additional element. Use {@link Builder} if
   * you're building up a set element by element.
   */
  public IdSet plus (long elem) {
    int bidx = Arrays.binarySearch(elems, elem);
    if (bidx >= 0) return this;

    int iidx = -(bidx+1);
    long[] nelems = new long[elems.length+1];
    System.arraycopy(elems, 0, nelems, 0, iidx);
    nelems[iidx] = elem;
    System.arraycopy(elems, iidx, nelems, iidx+1, elems.length-iidx);
    return new IdSet(nelems);
  }

  /** Returns the elements in {@code this} that are not in {@code other}. */
  public IdSet minus (IdSet other) {
    Builder b = new Builder(size());
    for (long id : elems) if (!other.contains(id)) b.add(id);
    return b.build();
  }

  @Override public int hashCode () {
    return Arrays.hashCode(elems);
  }

  @Override public boolean equals (Object other) {
    return (other instanceof IdSet) && Arrays.equals(elems, ((IdSet)other).elems);
  }

  @Override public String toString () {
    StringBuilder sb = new StringBuilder("{");
    for (long id : elems) {
      if (sb.length() > 1) sb.append(", ");
      sb.append(id);
    }
    return sb.append("}").toString();
  }

  @Override public Iterator<Long> iterator () {
    return new Iterator<Long>() {
      private int _idx = 0;
      public boolean hasNext () {
        return _idx < size();
      }
      public Long next () {
        return elems[_idx++];
      }
    };
  }
}
