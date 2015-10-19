//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.extract

import java.io.PrintWriter
import java.io.StringWriter
import java.net.URLClassLoader
import java.nio.file.Paths
import org.junit.Assert._
import org.junit._

import codex.TestScalaCodex

class ScalaExtractorTest {
  def extractor = TestScalaCodex.extractor(true)

  @Test def testBasics () {
    println(process("TestA.scala", Seq(
      "package pants",
      "package bottoms.cuffs",
      "object TestA {",
      "  class Inner {",
      "    val foo = 5",
      "    def bar (baz :Int) :Int = {",
      "      val bing = baz + 3",
      "      bing",
      "    }",
      "    def qux (s :String, c :Int) :Seq[String] = {",
      "      Seq(s)",
      "    }",
      "  }",
      "}")))
  }

  // @Test def testRefinement () {
  //   println(process("Refinement.scala", Seq(
  //     "package pants",
  //     "object TestA {",
  //     "  abstract class Abs {" ,
  //     "    def foo :Int",
  //     "  }",
  //     "  def inner = new Abs {",
  //     "    def foo = 3",
  //     "  }",
  //     "}")))
  // }

  // @Test def testSelect () {
  //   println(process("Select.scala", Seq(
  //     "package pants",
  //     "object TestA {",
  //     "  def foo (count :Int) {" ,
  //     "    Seq(count)",
  //     "    bar(count)",
  //     "    this.bar(count)",
  //     "    TestA.this.bar(count)",
  //     "  }",
  //     "  private def bar (count :Int) {",
  //     "  }",
  //     "}")))
  // }

  // @Test def testNew () {
  //   println(process("New.scala", Seq(
  //     "package pants",
  //     "import java.util.{ArrayList => JArrayList}",
  //     "object TestA {",
  //     "  class Abs {" ,
  //     "  }",
  //     "  val abs = new Abs",
  //     "  val nlist = new JArrayList",
  //     "  val slist = new JArrayList[String]",
  //     "}")))
  // }

  // TODO: too much desugaring happens in here?
  // @Test def testForComp () {
  //   println(process("ForComp.scala", Seq(
  //     "package pants",
  //     "object TestA {",
  //     "  val foo = for (info <- Seq(\"a\", \"b\", \"c\")) yield info",
  //     "}")))
  // }

  @Test def testMethodRefs () {
    println(process("MethodRefs.scala", Seq(
      "package pants",
      "class Foo {",
      "  val foo = 5",
      "  def bar (baz :Int) :Int = baz + foo",
      "}")))
  }

  private def process (name :String, code :Seq[String]) = {
    val cstr = code.mkString("\n")
    println(cstr)
    val out = new StringWriter
    extractor.process((name, cstr), new DebugWriter(new PrintWriter(out), cstr))
    out
  }
}
