//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex;

import codex.extract.JavaExtractor;
import codex.model.*;
import codex.store.*;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.junit.*;
import static org.junit.Assert.*;

public class SimpleCodexTest {

  public static EphemeralStore store;

  @BeforeClass public static void populateStore () throws IOException {
    store = new EphemeralStore();
    JavaExtractor extract = new JavaExtractor();
    // TODO: pass our classpath onto the extractor; create binary stores for the other jars?

    List<Path> sources = new ArrayList<>();
    Path root = Paths.get(System.getProperty("user.dir")).resolve("../core/src/main/java");
    if (!Files.exists(root)) return;

    Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
      public FileVisitResult visitFile (Path file, BasicFileAttributes attrs) {
        if (!attrs.isDirectory()) {
          String fname = file.getFileName().toString();
          if (fname.endsWith(".java")) sources.add(file);
        }
        return FileVisitResult.CONTINUE;
      }
    });

    extract.process(sources, store.writer);
    // dump(store);
    // System.out.println(store.defCount() + " defs.");
  }

  @AfterClass public static void clearStore () {
    store.close();
    store = null;
  }

  public Codex simpleCodex () {
    return new Codex.Simple(Collections.singletonList(store));
  }

  @Test public void testSimpleCodex () {
    Codex codex = simpleCodex();
    Ref locref = Ref.global("codex.model", "Ref", "Local");
    Optional<Def> locdef = codex.resolve(locref);
    assertTrue(locdef.isPresent());
    Def def = locdef.get();
    assertTrue(def.source().toString().endsWith("Ref.java"));
    assertEquals("public static final class Local extends Ref", def.sig().get().text);
  }

  @Test public void testFindName () {
    Codex codex = simpleCodex();
    List<Def> refdefs = codex.find(Codex.Query.name("ref"));
    assertFalse(refdefs.isEmpty()); // should be lots of 'ref's
    for (Def refdef : refdefs) {
      assertEquals("ref", refdef.name.toLowerCase());
      // dump(refdef);
    }

    List<Def> reftypes = codex.find(Codex.Query.name("ref").kind(Kind.TYPE));
    assertEquals(1, reftypes.size()); // should be only one Ref type
    for (Def refdef : reftypes) {
      assertTrue(store.source(refdef.id).toString().endsWith("Ref.java"));
    }
  }

  @Test public void testFindPrefix () {
    Codex codex = simpleCodex();
    List<Def> emits = codex.find(Codex.Query.prefix("EMIT"));
    assertTrue(emits.size() > 8); // should be quite a few emitFoo methods
    for (Def def : emits) {
      assertTrue(def.name.toLowerCase().startsWith("emit"));
      // dump(def);
    }
  }

  private void dump (Def def) {
    System.out.println(def);
    System.out.println(store.sig(def.id).map(s -> s.text).orElse("<no sig>") + " " +
                       store.source(def.id));
  }

  protected void dump (ProjectStore store) {
    for (Def def : store.topLevelDefs()) dump(store, def, "");
  }

  protected void dump (ProjectStore store, Def def, String indent) {
    System.out.print(indent);
    System.out.println(store.sig(def.id).map(s -> s.text).orElse(def.toString()));
    String mindent = indent + "  ";
    for (Def mdef : store.memberDefs(def.id)) {
      dump(store, mdef, mindent);
    }
  }
}
