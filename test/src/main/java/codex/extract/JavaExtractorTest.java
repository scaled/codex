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
    // System.out.println(out.toString());
  }

  public final String TESTLAM = Joiner.on("\n").join(
    "public class TestLam {",
    "    public static interface Fn {",
    "        int apply (String value);",
    "    }",
    "    public static void apply (Fn fn) {}",
    "    public static void main (String[] args) {",
    "        apply(v -> v.length());",
    "        apply((String q) -> q.length());",
    "    }",
    "}");

  @Test public void testLambdaParams () {
    JavaExtractor ex = new JavaExtractor();
    StringWriter out = new StringWriter();
    ex.process("TestLam.java", TESTLAM, new TextWriter(new PrintWriter(out)));
    // System.out.println(out.toString());
  }

  public final String TEST_OVERRIDES = Joiner.on("\n").join(
    "package foo.bar;",
    "public class TestOverrides {",
    "    public interface A {",
    "        public int foo ();",
    "    }",
    "    public static class B {",
    "        public int foo () { return 0; }",
    "    }",
    "    public static class C extends B implements A {",
    "        @Override public int foo () { return 1; }",
    "    }",
    "}");

  @Test public void testOverrides () {
    JavaExtractor ex = new JavaExtractor();
    StringWriter out = new StringWriter();
    ex.process("TestOverrides.java", TEST_OVERRIDES, new TextWriter(new PrintWriter(out)));
    String dump = out.toString();
    // C.foo() should override both A.foo() and B.foo()
    assertTrue("C.foo overrides A.foo",
               dump.contains("relation OVERRIDES foo.bar TestOverrides A foo()int"));
    assertTrue("C.foo overrides B.foo",
               dump.contains("relation OVERRIDES foo.bar TestOverrides B foo()int"));
  }
}
