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

class ScalaExtractorTest {

  final val TestA = Seq(
    "package pants",
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
    "}").mkString("\n")

  // locate the scala-library.jar
  val scalalib = {
    val loader = getClass.getClassLoader.asInstanceOf[URLClassLoader]
    val entries = loader.getURLs.toSeq.map(url => Paths.get(url.toURI))
    entries find(_.getFileName.toString.contains("scala-library")) getOrElse {
      throw new AssertionError("No scala-library.jar on test classpath? " + entries)
    }
  }

  def extractor = new ScalaExtractor() {
    def classpath = Seq(scalalib)
  }

  @Test def testBasics () {
    val out = new StringWriter()
    extractor.process(("TestA.scala", TestA), new DebugWriter(new PrintWriter(out), TestA))
    println(out)
  }
}
