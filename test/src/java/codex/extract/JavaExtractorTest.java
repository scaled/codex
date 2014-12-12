//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.extract;

import codex.extract.DebugWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.*;
import scaled.Seq;
import static org.junit.Assert.*;

public class JavaExtractorTest {

  private String testExtract (String file, String... code) {
    JavaExtractor ex = new JavaExtractor();
    StringWriter out = new StringWriter();
    String src = Seq.from(code).mkString("\n");
    ex.process(file, src, new DebugWriter(new PrintWriter(out), src));
    return out.toString();
  }

  @Test public void testBasics () {
    String out = testExtract(
      "TestA.java",
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
    // System.out.println(out);
  }

  @Test public void testLambdaParams () {
    String out = testExtract(
      "TestLam.java",
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
    // System.out.println(out);
  }

  @Test public void testOverrides () {
    String out = testExtract(
      "TestOverrides.java",
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
    // C.foo() should override both A.foo() and B.foo()
    assertTrue("C.foo overrides A.foo",
               out.contains("rel {type=OVERRIDES, tgt=foo.bar TestOverrides A foo()int}"));
    assertTrue("C.foo overrides B.foo",
               out.contains("rel {type=OVERRIDES, tgt=foo.bar TestOverrides B foo()int}"));
  }

  @Test public void testAnonCtor () {
    String out = testExtract(
      "TestAnonCtor.java",
      "package foo.bar;",
      "public class TestAnonCtor {",
      "    public Runnable foo = new Runnable() {", // super ctor is Object
      "        public void run () {}",
      "    };",
      "    public Thread foo = new Thread() {", // super ctor is real class
      "        public void run () {}",
      "    };",
      "}");
    // System.out.println(out);
    assertTrue("new Runnable() {} refs Object()",
               out.contains("use {tgt=java.lang Object Object()void, kind=FUNC"));
    assertTrue("new Thread() {} refs Thread()",
               out.contains("use {tgt=java.lang Thread Thread()void, kind=FUNC"));
  }
}
