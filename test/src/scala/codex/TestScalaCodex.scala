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

  def extractor (dbg :Boolean, cp :Seq[Path] = Seq()) = new ScalaExtractor() {
    def classpath = cp ++ Seq(scalalib)
    override protected def debug  = dbg
  }

  def main (args :Array[String]) {
    val store = new MapDBStore("test-codex", Paths.get("mapdb-codex"))
    try {
      (if (args.isEmpty) "help" else args(0)) match {
        case "index" => index(store, args(1))
        case  "tops" => tops(store)
        case  "dump" => dump(store, args(1))
        case _ =>
          println("Usage: TestCodex command")
          println("  command is one of:")
          println("    index [scala|path/to/some-source.jar]")
          println("    dump  refString (i.e. 'foo.bar Baz')")
          sys.exit(0)
      }
    } finally {
      store.close()
    }
  }

  val scalaVers = "2.12.0-M3"
  val home = System.getProperty("user.home")

  private def mavenJar (groupId :String, artId :String, vers :String, qual :String = "") :Path = {
    val groupPath = groupId.replace('.', '/')
    val suff = if (qual == "") "" else s"-$qual"
    Paths.get(s"$home/.m2/repository/$groupPath/$artId/$vers/$artId-$vers$suff.jar")
  }

  private def index (store :MapDBStore, what :String) {
    store.clear()

    val start = System.currentTimeMillis()
    what match {
      case "scala" =>
        val zip = mavenJar("org.scala-lang", "scala-library", scalaVers, "sources")
        extractor(false).process(new SourceSet.Archive(zip), store.writer)

      case "nsc" =>
        val cp = Seq(
          // mavenJar("org.scala-lang","scala-library",scalaVers),
          mavenJar("org.scala-lang", "scala-reflect", scalaVers),
          mavenJar("org.scala-lang.modules", s"scala-xml_$scalaVers", "1.0.5"),
          mavenJar("org.scala-lang.modules", s"scala-parser-combinators_$scalaVers", "1.0.4"),
          mavenJar("org.scala-lang.modules", "scala-asm", "5.0.4-scala-3"),
          mavenJar("org.apache.ant", "ant", "1.9.3"),
          mavenJar("jline", "jline", "2.12.1"))
        val zip = mavenJar("org.scala-lang", "scala-compiler", scalaVers, "sources")
        extractor(false, cp).process(new SourceSet.Archive(
          zip, ent => !ent.getName.contains("/partest/")), store.writer)

      case _ =>
        extractor(false).process(new SourceSet.Archive(Paths.get(what)), store.writer)
    }

    val end = System.currentTimeMillis()
    println("Extract and store: " + ((end-start)/1000L) + "s")

    println(store.defCount + " defs.")
    println(store.nameCount + " names.")
  }

  private def tops (store :MapDBStore) {
    for (top <- store.topLevelDefs()) {
      if (top.kind != Kind.SYNTHETIC) println(top)
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
    println(indent + sig + " (" + df.kind + ")")
    if (df.kind == Kind.TYPE) println(indent + "  (source: " + df.source + ")")
    val iter = df.members.iterator ; while (iter.hasNext) {
      val mdef = iter.next
      if (mdef.kind != Kind.SYNTHETIC) dump(indent + "  ", mdef)
    }
  }

  private def printDef (prefix :String, df :Def, suffix :String) {
    println(prefix + df.kind + " " + df.name + " " + df.id + suffix)
  }
}
