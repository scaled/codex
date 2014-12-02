//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex;

import codex.extract.JavaExtractor;
import codex.model.*;
import codex.store.*;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
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
import java.util.zip.ZipFile;
import org.junit.*;
import static org.junit.Assert.*;

public class SimpleCodexTest {

  public static List<Path> codexSources () throws IOException {
    List<Path> sources = new ArrayList<>();
    Path root = Paths.get(System.getProperty("user.dir")).resolve("../core/src/java");
    if (Files.exists(root)) {
      Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
        public FileVisitResult visitFile (Path file, BasicFileAttributes attrs) {
          if (!attrs.isDirectory()) {
            String fname = file.getFileName().toString();
            if (fname.endsWith(".java")) sources.add(file);
          }
          return FileVisitResult.CONTINUE;
        }
      });
    }
    return sources;
  }

  public static MapDBStore createCodexStore () throws Exception {
    MapDBStore store = new MapDBStore("test");
    List<Path> classpath = new ArrayList<>();
    for (URL url : ((URLClassLoader)SimpleCodexTest.class.getClassLoader()).getURLs()) {
      classpath.add(Paths.get(url.toURI()));
    }
    JavaExtractor extract = new JavaExtractor() {
      @Override public Iterable<Path> classpath () { return classpath; }
    };
    extract.process(codexSources(), store.writer());
    return store;
  }

  public static MapDBStore store;
  @BeforeClass public static void populateStore () throws Exception {
    store = createCodexStore();
  }
  @AfterClass public static void clearStore () {
    store.close();
    store = null;
  }

  @Test public void testSimpleCodex () {
    List<ProjectStore> stores = Collections.singletonList(store);
    Ref locref = Ref.global("codex.model", "Ref", "Local");
    Optional<Def> locdef = Ref.resolve(stores, locref);
    assertTrue("resolve(" + locref + ").isPresent", locdef.isPresent());
    Def def = locdef.get();
    assertTrue(def.source().toString().endsWith("Ref.java"));
    assertEquals("static final class Local extends Ref", def.sig().get().text);
  }

  @Test public void testFindName () {
    List<ProjectStore> stores = Collections.singletonList(store);
    List<Def> refdefs = Query.name("ref").find(stores);
    assertFalse(refdefs.isEmpty()); // should be lots of 'ref's
    for (Def refdef : refdefs) {
      assertEquals("ref", refdef.name.toLowerCase());
      // dump(refdef);
    }

    List<Def> reftypes = Query.name("ref").kind(Kind.TYPE).find(stores);
    assertEquals(1, reftypes.size()); // should be only one Ref type
    for (Def refdef : reftypes) {
      assertTrue(store.source(refdef.id).toString().endsWith("Ref.java"));
    }
  }

  @Test public void testFindPrefix () {
    List<ProjectStore> stores = Collections.singletonList(store);
    List<Def> emits = Query.prefix("EMIT").find(stores);
    assertTrue(emits.size() > 8); // should be quite a few emitFoo methods
    for (Def def : emits) {
      assertTrue(def.name.toLowerCase().startsWith("emit"));
      // dump(def);
    }
  }
}
