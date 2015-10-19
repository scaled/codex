//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex

import java.io.IOException
import java.net.URLClassLoader
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Optional
import java.util.zip.ZipFile

import codex.extract.ScalaExtractor
import codex.extract.SourceSet
import codex.model.Def
import codex.model.Kind
import codex.model.Ref
import codex.store.MapDBStore

object TestScalaCodex {

  // locate the scala-library.jar
  val scalalib = {
    val loader = classOf[Seq[_]].getClassLoader.asInstanceOf[URLClassLoader]
    val entries = loader.getURLs.toSeq.map(url => Paths.get(url.toURI))
    entries find(_.getFileName.toString.contains("scala-library")) getOrElse {
      throw new AssertionError("No scala-library.jar on test classpath? " + entries)
    }
  }

  def extractor = new ScalaExtractor() {
    def classpath = Seq(scalalib)
  }

  def main (args :Array[String]) {
    val store = new MapDBStore("test-codex", Paths.get("mapdb-codex"))
    try {
      (if (args.isEmpty) "help" else args(0)) match {
        case "index" => index(store, args(1))
        case  "tops" => tops(store)
        case  "dump" => dump(store, args(1))
        case _ =>
          System.err.println("Usage: TestCodex command")
          System.err.println("  command is one of:")
          System.err.println("    index [scala|path/to/some-source.jar]")
          System.err.println("    dump  refString (i.e. 'foo.bar Baz')")
          System.exit(0)
      }
    } finally {
      store.close()
    }
  }

  private def index (store :MapDBStore, what :String) {
    store.clear()

    val start = System.currentTimeMillis()
    val extract = extractor
    what match {
      case "scala" =>
        val scalaVers = "2.12.0-M3"
        val zip = Paths.get(System.getProperty("user.home") + "/.m2/repository/org/scala-lang/" +
          s"scala-library/$scalaVers/scala-library-$scalaVers-sources.jar")
        println(zip)
        extract.process(new SourceSet.Archive(zip), store.writer)

      case _ =>
        extract.process(new SourceSet.Archive(Paths.get(what)), store.writer)
    }

    val end = System.currentTimeMillis()
    System.err.println("Extract and store: " + ((end-start)/1000L) + "s")

    System.out.println(store.defCount + " defs.")
    System.out.println(store.nameCount + " names.")
  }

  private def tops (store :MapDBStore) {
    for (top <- store.topLevelDefs()) {
      if (top.kind != Kind.SYNTHETIC) System.out.println(top)
    }
  }

  private def dump (store :MapDBStore, what :String) {
    val defo = store.`def`(Ref.Global.fromString(what))
    if (!defo.isPresent) System.err.println("No def found for '" + what + "'.")
    else dump("", defo.get)
  }

  private def dump (indent :String, df :Def) {
    // printDef(indent, def, "")
    val sig = if (df.sig.isPresent) df.sig.get.text else (df.kind + " " + df.name)
    System.out.println(indent + df.sig)
    if (df.kind == Kind.TYPE) System.out.println(indent + "  (source: " + df.source + ")")
    val iter = df.members.iterator ; while (iter.hasNext) {
      val mdef = iter.next
      if (mdef.kind != Kind.SYNTHETIC) dump(indent + "  ", mdef)
    }
  }

  private def printDef (prefix :String, df :Def, suffix :String) {
    System.out.println(prefix + df.kind + " " + df.name + " " + df.id + suffix)
  }
}
