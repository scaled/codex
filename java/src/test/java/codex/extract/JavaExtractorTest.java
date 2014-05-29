//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.extract;

import codex.extract.TextWriter;
import com.google.common.base.Joiner;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.*;
import static org.junit.Assert.*;

public class JavaExtractorTest {

  public final String TESTA = Joiner.on("\n").join(
    "package foo.bar;",
    "public class TestA {",
    "    public static class A {",
    "        public int value;",
    "    }",
    "    public static class B {",
    "        public void noop () {",
    "        }",
    "    }",
    "    public static void main (String[] args) {",
    "        int av = new A().value;",
    "        B b = new B();",
    "        b.noop();",
    "    }",
    "}");

  @Test public void testBasics () {
    JavaExtractor ex = new JavaExtractor();
    StringWriter out = new StringWriter();
    ex.process("TestA.java", TESTA, new TextWriter(new PrintWriter(out)));
    System.out.println(out.toString());
  }
}
