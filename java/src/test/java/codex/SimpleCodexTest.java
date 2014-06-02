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
import java.util.List;
import java.util.Optional;
import org.junit.*;
import static org.junit.Assert.*;

public class SimpleCodexTest {

  @Test public void testSimpleCodex () throws IOException {
    EphemeralStore store = new EphemeralStore(1);
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

    Codex codex = new Codex(Collections.singletonList(store));
    Ref locref = Ref.global("codex.model", "Ref", "Local");
    Optional<DefInfo> locinf = codex.resolve(locref);
    assertTrue(locinf.isPresent());
    DefInfo di = locinf.get();
    assertTrue(di.source.toString().endsWith("Ref.java"));
    assertEquals("public static final class Local extends Ref", di.sig.get().text);
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
