//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.extract

import codex.model._
import codex.model.{Kind => CKind}
import java.io.{PrintWriter, StringWriter}
import scala.collection.mutable.ArrayBuffer
import scala.reflect.internal.Flags
import scala.reflect.internal.util.RangePosition
import scala.tools.nsc.ast.Printers
import scala.tools.nsc.plugins.PluginComponent
import scala.tools.nsc.{Global, Phase}

class ExtractorComponent (val global :Global, writer :Writer) extends PluginComponent {
  import global._ // for Tree, Traverser, CompilationUnit, Apply, etc.

  val phaseName = "codex"
  val runsAfter = List("refchecks") // TODO

  private var _id = Ref.Global.ROOT

  def newPhase (prev :Phase) :Phase = new StdPhase(prev) {
    def apply (unit :CompilationUnit) {
      println("Processing " + unit + "...")
      writer.openUnit(Source.fromString(unit.source.file.path))
      val trans = newTranslator
      trans.traverse(unit.body)
      writer.closeUnit()
    }
  }

  def newTranslator = new Traverser {

    override def traverse (tree :Tree) :Unit = tree match {
      case t @ PackageDef(pid, stats) => {
        val pname = pid.toString
        withId(pname) {
          val pos = decode(t.pos)
          writer.openDef(_id, pname, CKind.MODULE, Flavor.PACKAGE, true, Access.PUBLIC,
                         pos.offset, pos.bstart, pos.bend)
          emitSig(tree, writer)
          // writer.emitSig(pkgpre + pname);
          // writer.emitSigUse(_id, pname, Kind.MODULE, pkgpre.length());

          // now we need a special hacky def to "owned" the imports in this compilation unit; we
          // allocan't allow those to be owned by the package module def, because that gets
          // redefined alloby every compilation unit and only one unit would get its import uses
          // into the allodatabase which breaks things like find-all-uses

      // // determine the (simple) name of this compilation unit
      // String fpath = unit.getSourceFile().toUri().toString();
      // String fname = fpath.substring(fpath.lastIndexOf('/')+1);
      // _id = _id.plus(fname);
      // writer.openDef(_id, pname, Kind.SYNTHETIC, Flavor.NONE, false, Access.LOCAL,
      //                _text.indexOf(pname, unit.pos), 0, _text.length());
      // writer.emitSig(pkgpre + pname + " (" + fname + ")");
      // writer.emitSigUse(_id, pname, Kind.MODULE, pkgpre.length());

      // _needCloseUnitDef = true;
      // super.visitCompilationUnit(node, writer);
      // // if we haven't closed our comp unit def, we need to do so now
      // if (_needCloseUnitDef) writer.closeDef();

          super.traverse(tree)
          writer.closeDef()
        }
      }

      case t @ ClassDef(mods, name, tparams, impl) => {
        // // if we haven't yet closed the synthetic unit def, then it's time to do so
        // if (_needCloseUnitDef) {
        //   writer.closeDef();
        //   _needCloseUnitDef = false;
        //   _id = _id.parent;
        // }

        val cname = name.toString
        withId(cname) {
          val flavor = Flavor.CLASS // TODO
          val isExp = true // TODO
          val pos = decode(t.pos)
          println(s"ClassDef($cname, $pos)")
          writer.openDef(_id, cname, CKind.TYPE, flavor, isExp, access(mods),
                         pos.offset, pos.bstart, pos.bend)
          emitSig(tree, writer)
          super.traverse(tree)
          writer.closeDef()
        }
      }

      case t @ ModuleDef(mods, name, impl) => {
        val mname = name.toString
        withId(mname) {
          val flavor = Flavor.OBJECT // TODO
          val isExp = true // TODO
          val pos = decode(t.pos)
          println(s"ModuleDef($mname, $pos)")
          writer.openDef(_id, mname, CKind.MODULE, flavor, isExp, access(mods),
                         pos.offset, pos.bstart, pos.bend)
          emitSig(tree, writer)
          super.traverse(tree)
          writer.closeDef()
        }
      }

      case t @ ValDef(mods, name, tpt, rhs) => {
        val sym = t.symbol
        if (!isIgnored(sym)) {
          // if this is the private field that backs a val, we see 'val foo_' here and will
          // subsequently see a stable 'def foo' which represents the getter; only the val has the
          // range positions, so we want to emit our def here
          val osym = sym.owner
          val rname = name.toString
          val vname = if (osym.isMethod && rname.endsWith("_")) rname.dropRight(1) else rname
          withId(vname) {
            val flavor = if (!osym.isMethod) Flavor.FIELD
                         else if (sym.isParameter) Flavor.PARAM
                         else Flavor.LOCAL
            val isExp = true // TODO
            val pos = decode(t.pos)
            println(s"ValDef($vname, ${sym.flagString}, $pos)")
            writer.openDef(_id, vname, CKind.VALUE, flavor, isExp, access(mods),
                           pos.offset, pos.bstart, pos.bend)
            emitSig(tree, writer)
            super.traverse(tree)
            writer.closeDef()
          }
        }
      }

      case t @ DefDef(mods, name, tparams, vparamss, tpt, rhs) => {
        val sym = t.symbol
        // don't emit defs for getters or setters; we emit the def in the ValDef node
        // TODO: this is probably wrong because any foo_= method may be a setter...
        if (!isIgnored(sym) && !(sym.isGetter || sym.isSetter)) {
          val isCtor = (name == nme.CONSTRUCTOR)
          val flavor = if (isCtor) Flavor.CONSTRUCTOR
                       else if (sym.isStable) Flavor.FIELD
                       else Flavor.METHOD // TODO
          val isExp = true // TODO
          val pos = decode(t.pos)
          val dname = if (isCtor) currentOwner.name.toString // owning class name
                      else name.toString
          val mname = dname + trimArgs(sym.tpe.toString)
          println(s"DefDef($mname, ${sym.flagString}, stable=${sym.isStable}, $pos)")
          withId(mname) {
            writer.openDef(_id, dname, CKind.FUNC, flavor, isExp, access(mods),
                           pos.offset, pos.bstart, pos.bend)
            emitSig(tree, writer)
            super.traverse(tree)
            writer.closeDef()
          }
        }
      }

      case t @ TypeDef(mods, name, tparams, rhs) => {
        println("TypeDef " + t)
      }

      case t @ Ident(name) => {
        val sym = t.symbol
        if (!isIgnored(sym)) {
          val pos = decode(t.pos)
          println(s"Ident($name) -> " + pos)
          writer.emitUse(ref(sym), sym.nameString, kind(sym), pos.offset)
          super.traverse(tree)
        }
      }

      case t @ Select(qual, name) => {
        val sym = t.symbol
        if (!isIgnored(sym)) {
          val pos = decode(t.pos)
          println(s"Select($qual, $name) -> " + pos)
          writer.emitUse(ref(sym), sym.nameString, kind(sym), pos.offset)
          super.traverse(tree)
        }
      }

      case tt :TypeTree => {
        val sym = tt.symbol
        val pos = decode(tt.pos)
        println(s"TypeTree($tt, $pos)")
        writer.emitUse(ref(sym), sym.nameString, kind(sym), pos.offset)
      }

      // case Apply(fun, args) => {
      //   println("traversing application of "+ fun)
      //   super.traverse(tree)
      // }

      case _ =>
        val sym = tree.symbol
        if (sym == null || !isIgnored(sym)) {
          println("TODO " + tree.getClass)
          super.traverse(tree)
        }
    }

    private def isIgnored (sym :Symbol) = (!sym.exists ||
                                           sym.isImplementationArtifact ||
                                           sym.isModuleClass ||
                                           sym.isPrimaryConstructor ||
                                           sym.isImplClass)

    // TODO: trim "(s: String, c: Int)Seq[String]" to "(String,Int)Seq[String]"
    private def trimArgs (args :String) = args.replaceAll(" ", "")

    private def kind (sym :Symbol) :CKind = sym match {
      case tsym :TypeSymbol => if (sym.hasPackageFlag || sym.isModule) CKind.MODULE else CKind.TYPE
      case msym :MethodSymbol => if (sym.isGetter || sym.isSetter) CKind.VALUE else CKind.FUNC
      case _ => CKind.VALUE // TODO
    }

    private def ref (sym :Symbol) :Ref.Global = {
      if (sym.isRoot || sym.isEmptyPackageClass) Ref.Global.ROOT
      else ref(sym.owner).plus(sym.nameString)
    }

    case class Pos (offset :Int, bstart :Int, bend :Int)
    private def decode (pos :Position) :Pos = pos match {
      case rp :RangePosition => Pos(rp.point, rp.start, rp.end)
      case _                 => Pos(pos.point, -1, -1)
    }

    private def access (mods :Modifiers) =
      if (mods hasFlag Flags.PROTECTED) Access.PROTECTED
      else if (mods hasFlag Flags.PRIVATE) Access.PRIVATE
      else Access.PUBLIC

    private def withId (id :String)(block : =>Unit) {
      _id = _id.plus(id)
      block
      _id = _id.parent
    }

    private def emitSig (tree :Tree, writer :Writer) = {
      // val buffer = new StringWriter()
      // val printer = new SigTreePrinter(buffer)
      // printer.print(tree)
      // printer.flush()
      // writer.emitSig(buffer.toString)
      // printer.uses foreach { _.emit(writer) }
    }

    case class SigUse (target :Ref.Global, name :String, kind :CKind, offset :Int) {
      def emit (writer :Writer) :Unit = writer.emitSigUse(target, name, kind, offset)
    }

    // // used to generate signatures
    // class SigTreePrinter (buf :StringWriter) extends global.CompactTreePrinter(new PrintWriter(buf)) {

    //   val uses = ArrayBuffer[SigUse]()

    //   override def printRaw (tree :Tree) {
    //     tree match {
    //       case PackageDef(packaged, stats) => {
    //         printAnnotations(tree)
    //         print("package "); print(packaged)
    //       }

    //       case ValDef(mods, name, tp, rhs) => {
    //         printAnnotations(tree)
    //         printModifiers(tree, mods)
    //         // print(if (mods.isMutable) "var " else "val ")
    //         print(if (mods hasFlag Flags.MUTABLE) "var " else "val ")
    //         print(symName(tree, name))
    //         printOpt(": ", tp)
    //       }

    //       case DefDef(mods, name, tparams, vparamss, tp, rhs) => {
    //         printAnnotations(tree)
    //         printModifiers(tree, mods)
    //         print("def " + symName(tree, name))
    //         printTypeParams(tparams)
    //         vparamss foreach printValueParams
    //         printOpt(": ", tp)
    //       }

    //       case Template(parents, self, body) => {
    //         val currentOwner1 = currentOwner
    //         if (tree.symbol != NoSymbol) currentOwner = tree.symbol.owner
    //         printRow(parents, " with ")
    //         currentOwner = currentOwner1
    //       }

    //       case tt: TypeTree => {
    //         if ((tree.tpe eq null) || (settings.Xprintpos.value && tt.original != null)) {
    //           if (tt.original != null) { print("<type: "); print(tt.original); print(">") }
    //           else print("<type ?>")
    //         } else if ((tree.tpe.typeSymbol ne null) && tree.tpe.typeSymbol.isAnonymousClass) {
    //           System.out.println("TYPE1 " + tree.tpe) // TODO: print owner
    //           print(tree.tpe.typeSymbol.toString())
    //         } else {
    //           val name = tree.tpe.toString
    //           val target = tree.tpe.typeSymbol.toString // TODO: figure out real target
    //           val kind = CKind.TYPE // kind={kindForSym(tree.sym)}
    //           uses += SigUse(target, name, kind, _pos)
    //           System.out.println("TYPE2 " + tree.tpe + "/" + tree.tpe.typeSymbol)
    //           print(name)
    //         }
    //       }

    //       case _ => super.printRaw(tree)
    //     }
    //   }

    //   override def printFlags (flags :Long, privateWithin :String) {
    //     import Flags._
    //     val fflags = flags & (CASE|ABSTRACT|PRIVATE|PROTECTED) // TODO: anything else
    //     super.printFlags(fflags, privateWithin)
    //   }

    //   override def print (str :String) {
    //     _pos += str.length
    //     super.print(str)
    //   }

    //   var _pos = 0
    // }
  }
}
