//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.extract

import java.lang.{Iterable => JIterable}
import java.nio.file.Path
import scala.reflect.internal.Positions
import scala.reflect.internal.util.{BatchSourceFile, SourceFile}
import scala.reflect.io.{AbstractFile, VirtualDirectory}
import scala.tools.nsc.reporters.ConsoleReporter
import scala.tools.nsc.util.ClassPath
import scala.tools.nsc.{Global, Settings}

abstract class ScalaExtractor extends Extractor {
  import scala.collection.convert.WrapAsScala._

  /** Provides the classpath used by the compiler. */
  def classpath :Iterable[Path]

  override def process (files :JIterable[Path], writer :Writer) {
    process0(files.toList.map(f => new BatchSourceFile(AbstractFile.getFile(f.toFile))), writer)
  }

  /** Processes the test `file` `(name, code)`. Metadata is emitted to `writer`. */
  def process (file :(String, String), writer :Writer) :Unit = process(List(file), writer)

  /** Processes test `files` (a list of `(name, code)` pairs). Metadata is emitted to `writer`. */
  def process (files :List[(String,String)], writer :Writer) {
    process0(files.map { case (name, code) => new BatchSourceFile(name, code) }, writer)
  }

  private def process0 (sources :List[SourceFile], writer :Writer) {
    val settings = new Settings
    settings.classpath.value = ClassPath.join(classpath.map(_.toString).toSeq :_*)
    // save class files to a virtual directory in memory (TODO: how to disable class gen?)
    settings.outputDirs.setSingleOutput(new VirtualDirectory("(memory)", None))

    val compiler = new Global(settings, new ConsoleReporter(settings)) with Positions {
      val rcomp = new ExtractorComponent(this, writer)
      override protected def computeInternalPhases () {
        super.computeInternalPhases
        phasesSet += rcomp
      }
    }

    writer.openSession()
    try new compiler.Run().compileSources(sources)
    finally {
      writer.closeSession()
    }
  }
}
