//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.zip.ZipFile;

import codex.extract.JavaExtractor;
import codex.model.Def;
import codex.model.Kind;
import codex.model.Ref;
import codex.store.MapDBStore;

public class TestCodex {

  public static void main (String[] args) throws IOException {
    MapDBStore store = new MapDBStore("test-codex", Paths.get("mapdb-codex"));
    try {
      String cmd = (args.length < 1) ? "help" : args[0];
      switch (cmd) {
      case "index":
        index(store, args[1]);
        break;

      case "tops":
        tops(store);
        break;

      case "dump":
        dump(store, args[1]);
        break;

      default:
        System.err.println("Usage: TestCodex command");
        System.err.println("  command is one of:");
        System.err.println("    index [guava|jdk|path]");
        System.err.println("    dump  refString (i.e. 'foo.bar Baz')");
        System.exit(0);
        break;
      }
    } finally {
      store.close();
    }
  }

  private static void index (MapDBStore store, String what) throws IOException {
    store.clear();

    long start = System.currentTimeMillis();
    JavaExtractor extract = new JavaExtractor().summaryMode();
    switch (what) {
    case "guava":
      {
        String zip = System.getProperty("user.home") +
          "/.m2/repository/com/google/guava/guava/16.0.1/guava-16.0.1-sources.jar";
        extract.process(new ZipFile(zip), store.writer());
      } break;

    case "jdk":
      {
        String zip = System.getProperty("java.home") + "/../src.zip";
        extract.process(new ZipFile(zip), e -> e.getName().startsWith("java"), store.writer());
      } break;

    default:
      extract.process(new ZipFile(what), store.writer());
      break;
    }

    long end = System.currentTimeMillis();
    System.err.println("Extract and store: " + ((end-start)/1000L) + "s");

    System.out.println(store.defCount() + " defs.");
    System.out.println(store.nameCount() + " names.");
  }

  private static void tops (MapDBStore store) {
    for (Def top : store.topLevelDefs()) {
      if (top.kind != Kind.SYNTHETIC) System.out.println(top);
    }
  }

  private static void dump (MapDBStore store, String what) {
    Optional<Def> defo = store.def(Ref.Global.fromString(what));
    if (!defo.isPresent()) System.err.println("No def found for '" + what + "'.");
    else dump("", defo.get());
  }

  private static void dump (String indent, Def def) {
    // printDef(indent, def, "");
    System.out.println(indent + def.sig().map(sig -> sig.text).orElse(def.kind + " " + def.name));
    if (def.kind == Kind.TYPE) System.out.println(indent + "  (source: " + def.source() + ")");
    for (Def mdef : def.members()) {
      if (mdef.kind != Kind.SYNTHETIC) dump(indent + "  ", mdef);
    }
  }

  private static void printDef (String prefix, Def def, String suffix) {
    System.out.println(prefix + def.kind + " " + def.name + " " + def.id + suffix);
  }
}
