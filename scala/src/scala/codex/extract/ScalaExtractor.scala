//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.extract

import java.io.{PrintWriter, StringWriter}
import java.lang.{Iterable => JIterable}
import java.nio.file.Path
import java.util.zip.ZipFile
import scala.collection.mutable.ArrayBuffer
import scala.reflect.internal.Positions
import scala.reflect.internal.util.{BatchSourceFile, SourceFile}
import scala.reflect.io.{AbstractFile, VirtualDirectory, VirtualFile, ZipArchive}
import scala.tools.nsc.reporters.ConsoleReporter
import scala.tools.nsc.util.ClassPath
import scala.tools.nsc.{Global, Settings}

abstract class ScalaExtractor extends Extractor {
  import scala.collection.JavaConverters._

  /** Provides the classpath used by the compiler. */
  def classpath :Iterable[Path]

  override def process (sources :SourceSet, writer :Writer) = sources match {
    case sf :SourceSet.Files => process0(
      (List() ++ sf.paths.asScala).map(
        f => new BatchSourceFile(AbstractFile.getFile(f.toFile))), writer)
    case sa :SourceSet.Archive =>
      process0(zipToVirtualFiles(sa).map(new BatchSourceFile(_)).toList, writer)
  }

  /** Processes the test `file` `(name, code)`. Metadata is emitted to `writer`. */
  def process (file :(String, String), writer :Writer) :Unit = process(List(file), writer)

  /** Processes test `files` (a list of `(name, code)` pairs). Metadata is emitted to `writer`. */
  def process (files :List[(String,String)], writer :Writer) {
    process0(files.map { case (name, code) => new BatchSourceFile(name, code) }, writer)
  }

  /** Override and set to true when debugging. */
  protected def debug = false

  /** Extra arguments to pass to the compiler. */
  protected def compilerArgs :List[String] = Nil

  /** Output from extractor compiler is routed through here. */
  protected def log (msg :String) = println(msg)

  private def process0 (sources :List[SourceFile], writer :Writer) {
    val settings = new Settings(log)
    settings.processArguments(compilerArgs, true)
    settings.classpath.value = ClassPath.join(classpath.map(_.toString).toSeq :_*)
    settings.Yrangepos.value = true
    // save class files to a virtual directory in memory (TODO: how to disable class gen?)
    settings.outputDirs.setSingleOutput(new VirtualDirectory("(memory)", None))

    val logWriter = new StringWriter() {
      override def flush () {
        log(toString)
        getBuffer.setLength(0)
      }
    }
    val reporter = new ConsoleReporter(settings, Console.in, new PrintWriter(logWriter))
    val compiler = new Global(settings, reporter) with Positions {
      val rcomp = new ExtractorComponent(this, writer, debug)
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

  private def zipToVirtualFiles (sa :SourceSet.Archive) :Seq[VirtualFile] = {
    val archive = new ZipFile(sa.archive.toFile)
    val archiveFile = AbstractFile.getFile(sa.archive.toFile)
    val files = ArrayBuffer[VirtualFile]()
    val enum = archive.entries ; while (enum.hasMoreElements) {
      val entry = enum.nextElement
      if (sa.filter.test(entry) && (entry.getName.endsWith(".scala") ||
                                    entry.getName.endsWith(".java"))) {
        files += new VirtualFile(entry.getName, entry.getName) {
          override def lastModified = entry.getTime
          override def input        = archive getInputStream entry
          override def sizeOption   = Some(entry.getSize.toInt)
          override def underlyingSource = Some(archiveFile)
        }
      }
    }
    files
  }
}
