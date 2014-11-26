//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.extract;

import codex.model.*;
import codex.store.*;
import com.google.common.base.Joiner;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;
import org.junit.*;
import static org.junit.Assert.*;

public class TokenExtractorTest {

  public final String TESTA = Joiner.on("\n").join(
    "package com.test",
    "",
    "object Foo {",
    "  class Bar {",
    "    def baz () {}",
    "    val BAZ = 1",
    "  }",
    "  trait Bippy {",
    "    def bangle ()",
    "  }",
    "  def fiddle (foo :Int, bar :Int) = monkey",
    "  def faddle (one :Int, two :String) = {",
    "    def nested1 (thing :Bippy) = ...",
    "    def nested2 (thing :Bippy) = {}",
    "  }",
    "}",
    "",
    "def outer (thing :Bippy) = ...");

  /*@Test*/ public void testDump () throws IOException {
    TokenExtractor ex = new TokenExtractor();
    StringWriter out = new StringWriter();
    ex.process("TestA.scala", TESTA, new TextWriter(new PrintWriter(out)));
    System.out.println(out.toString());
  }

  @Test public void testBasics () throws IOException {
    TokenExtractor ex = new TokenExtractor();
    ProjectStore store = new MapDBStore("test");
    ex.process("TestA.scala", TESTA, store.writer());
    // store.visit(new Source.File("TestA.scala"), el -> System.out.println(el));

    Ref.Global baz = Ref.Global.fromString("com.test Foo Bar baz");
    Optional<Def> bdef = store.def(baz);
    assertTrue(bdef.isPresent());
    assertEquals("baz", bdef.get().name);
  }

  public final String LIST = Joiner.on("\n").join(
    "package com.test",
    "",
    "abstract class List[+A] extends Iterable[A] {",
    "  def map[B] (f :(A => B)) :List[B]",
    "}");

  @Test public void testTypeParams () throws IOException {
    TokenExtractor ex = new TokenExtractor();
    ProjectStore store = new MapDBStore("test");
    ex.process("List.scala", LIST, store.writer());
    // store.visit(new Source.File("List.scala"), el -> System.out.println(el));

    Ref.Global map = Ref.Global.fromString("com.test List map");
    Optional<Def> mdef = store.def(map);
    assertTrue(mdef.isPresent());
    assertEquals("map", mdef.get().name);
  }
}
