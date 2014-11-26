//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.model;

import codex.SimpleCodexTest;
import codex.store.MapDBStore;
import codex.store.ProjectStore;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.*;
import static org.junit.Assert.*;

public class OOTest {

  public static MapDBStore store;
  @BeforeClass public static void populateStore () throws Exception {
    store = SimpleCodexTest.createCodexStore();
  }
  @AfterClass public static void clearStore () {
    store.close();
    store = null;
  }

  @Test public void testResolveMethods () {
    List<ProjectStore> stores = Collections.singletonList(store);
    Def usedef = Ref.resolve(stores, Ref.global("codex.model", "Use")).get();
    Set<Ref.Global> refs = new HashSet<>();
    for (Def meth : OO.resolveMethods(stores, usedef)) refs.add(meth.globalRef());
    assertTrue(refs.contains(Ref.Global.fromString("codex.model Use kind()codex.model.Kind")));
    // this will have been overridden by the above
    assertFalse(refs.contains(Ref.Global.fromString("codex.model Element kind()codex.model.Kind")));
    // this will not and should have come from Element
    assertTrue(refs.contains(Ref.Global.fromString(
      "codex.model Element compareTo(codex.model.Element)int")));
  }
}
