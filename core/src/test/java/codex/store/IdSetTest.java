//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.store;

import org.junit.*;
import static org.junit.Assert.*;

public class IdSetTest {

  @Test public void empty () {
    assertFalse(IdSet.EMPTY.contains(1));
  }

  @Test public void basics () {
    IdSet one = IdSet.EMPTY.plus(1);
    assertTrue(one.contains(1));
    assertFalse(one.contains(2));

    IdSet two = IdSet.builder().add(1).add(2).build();
    assertTrue(one.contains(1));
    assertFalse(one.contains(2));
    assertTrue(two.contains(1));
    assertTrue(two.contains(2));
    assertFalse(two.contains(3));

    // assertTrue(one == one.plus(1));
    assertTrue(two == two.plus(1));
    assertTrue(two == two.plus(2));
  }

  @Test public void edgeCases () {
    IdSet min = IdSet.builder().add(Long.MIN_VALUE).build();
    assertTrue(min.contains(Long.MIN_VALUE));
    assertFalse(min.contains(Long.MAX_VALUE));

    IdSet max = IdSet.builder().add(Long.MAX_VALUE).build();
    assertTrue(max.contains(Long.MAX_VALUE));
    assertFalse(max.contains(Long.MIN_VALUE));

    IdSet minMax = IdSet.builder().add(Long.MIN_VALUE).add(Long.MAX_VALUE).build();
    assertTrue(minMax.contains(Long.MAX_VALUE));
    assertTrue(minMax.contains(Long.MIN_VALUE));
  }

  @Test public void builder () {
    IdSet.Builder b = new IdSet.Builder();
    b.add(3).add(3).add(3).add(22).add(22).add(1).add(6).add(22).add(22).add(22).add(14);
    IdSet s = b.build();
    assertEquals(5, s.size());
  }
}
