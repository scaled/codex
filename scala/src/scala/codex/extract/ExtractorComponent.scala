//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.extract

import codex.model._
import codex.model.{Kind => CKind}
import java.io.{PrintWriter, StringWriter}
import scala.collection.mutable.{ArrayBuffer, Set => MSet}
import scala.reflect.internal.Flags
import scala.reflect.internal.util.RangePosition
import scala.tools.nsc.ast.Printers
import scala.tools.nsc.plugins.PluginComponent
import scala.tools.nsc.{Global, Phase}

class ExtractorComponent (val global :Global, writer :Writer) extends PluginComponent {
  import global._ // for Tree, Traverser, CompilationUnit, Apply, etc.

  override val phaseName = "codex"
  override val runsAfter = "typer" :: Nil
  override val runsBefore = "patmat" :: Nil

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
    private var _curpos :Pos = Pos(0, 0, Int.MaxValue)
    private val _ignores = MSet[Tree]()
    private var _lastField :ValDef = _

    private def openDef (name :String, kind :CKind, flavor :Flavor, isExp :Boolean, acc :Access) {
      writer.openDef(_id, name, kind, flavor, isExp, acc,
                     _curpos.offset, _curpos.start, _curpos.end)
    }

    private def emitUse (whence :String, sym :Symbol, name :String, tpos :Position) :Unit =
      emitUse(whence, sym, name, decode(tpos))
    private def emitUse (whence :String, sym :Symbol, name :String, pos :Pos) {
      // positions without start/end or with bogus offset have generally been inserted during a
      // later compiler phase (e.g. type inference) and are not in the source code; so ignore
      if (pos.start >= 0 && pos.offset < _curpos.end) {
        println(s"$whence.emitUse($name, $pos)")
        writer.emitUse(ref(sym), kind(sym), pos.offset, name)
      }
    }
    private def emitUse (whence :String, sym :Symbol, pos :Pos) {
      // positions without start/end or with bogus offset have generally been inserted during a
      // later compiler phase (e.g. type inference) and are not in the source code; so ignore
      if (pos.start >= 0 && pos.offset < _curpos.end) {
        println(s"$whence.emitUse($pos)")
        writer.emitUse(ref(sym), kind(sym), pos.offset, pos.end-pos.offset)
      }
    }

    override def traverse (tree :Tree) :Unit = tree match {
      case t @ PackageDef(pid, stats) => {
        val pname = pkgName(t.symbol)
        withTree(pname, t) {
          openDef(pname, CKind.MODULE, Flavor.PACKAGE, true, Access.PUBLIC)
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

          // we don't do super.traverse here because we don't want to traverse the package ident
          // which would result in a use being emitted
          traverseStats(stats, t.symbol)
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

        val sym = t.symbol
        if (!isIgnored(sym)) {
          val cname = name.toString
          withTree(cname, t) {
            val kind = if (sym.isModuleClass) CKind.MODULE else CKind.TYPE
            val flavor = if (sym.isModuleClass) Flavor.OBJECT else Flavor.CLASS // TODO: trait
            val isExp = true // TODO
            println(s"ClassDef($cname) ${_curpos}")
            openDef(cname, kind, flavor, isExp, access(mods))
            emitSig(tree, writer)
            super.traverse(tree)
            writer.closeDef()
          }
        }
      }

      case t @ ModuleDef(mods, name, impl) => {
        val mname = name.toString
        withTree(mname, t) {
          val flavor = Flavor.OBJECT // TODO
          val isExp = true // TODO
          println(s"ModuleDef($mname) ${_curpos}")
          openDef(mname, CKind.MODULE, flavor, isExp, access(mods))
          emitSig(tree, writer)
          super.traverse(tree)
          writer.closeDef()
        }
      }

      case t @ ValDef(mods, name, tpt, rhs) => {
        val sym = t.symbol
        val vname = name.toString
        // valdefs with a space at the end of the name are the private backing fields for vals,
        // which we don't want to emit here, we'll emit them when we see the getter method; but we
        // do need to save the range position for this val because the defdef won't have it, yay!
        if (vname endsWith " ") {
          _lastField = t
        }
        else if (!isIgnored(sym)) withTree(vname, t) {
          val osym = sym.owner
          val flavor = if (!osym.isMethod) Flavor.FIELD
          else if (sym.isParameter) Flavor.PARAM
          else Flavor.LOCAL
          val isExp = true // TODO
          println(s"ValDef($vname, ${sym.flagString}) ${_curpos}")
          openDef(vname, CKind.VALUE, flavor, isExp, access(mods))
          emitSig(tree, writer)
          // if our type-tree is inferred (the only way we can tell that is if it has the same
          // position as this tree), mark it as to be ignored when we traverse it
          if (tpt.pos.point == t.pos.point) _ignores += tpt
          super.traverse(tree)
          _ignores -= tpt
          writer.closeDef()
        }
      }

      case t @ DefDef(mods, name, tparams, vparamss, tpt, rhs) => {
        val sym = t.symbol
        // don't emit defs for the primary constructor, that's handled by the ClassDef node
        // don't emit defs for setters; TODO: this is probably wrong because any foo_= method may
        // be a setter...
        if (!isIgnored(sym) && !(sym.isPrimaryConstructor || sym.isSetter)) {
          val mname = nameFromSym(sym)
          // if this is the stable DefDef getter that represents the most recently traversed
          // ValDef, then use the ValDef's tree in withTree so that we get its range positions; the
          // DefDef has no range positions but the ValDef does
          val dt = if (sym.isStable && _lastField != null && (s"$name " == s"${_lastField.name}")) _lastField
                   else t
          withTree(mname, dt) {
            val isCtor = (name == nme.CONSTRUCTOR)
            val flavor = if (isCtor) Flavor.CONSTRUCTOR
                         else if (sym.isStable) Flavor.FIELD
                         else Flavor.METHOD // TODO
            val isExp = true // TODO
            val dname = if (isCtor) currentOwner.name.toString // owning class name
                        else name.toString // bare name of the def, no type info
            println(s"DefDef($mname, ${sym.flagString}, stable=${sym.isStable}) ${_curpos}")
            openDef(dname, CKind.FUNC, flavor, isExp, access(mods))
            emitSig(tree, writer)
            // if our type-tree is inferred (the only way we can tell that is if it has the same
            // position as this tree), mark it as to be ignored when we traverse it
            if (tpt.pos.point == t.pos.point) _ignores += tpt
            super.traverse(tree)
            _ignores -= tpt
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
          emitUse("Ident", sym, name.toString, t.pos)
          super.traverse(tree)
        }
      }

      case t @ New(tpt) => {
        println(s"New($tpt) ${decode(t.pos)}")
        super.traverse(t)
      }

      case t @ Select(qual, name) => {
        val sym = t.symbol
        println(s"Select($qual, $name) (${isIgnored(sym)})")
        if (!isIgnored(sym)) {
          // either this select node or our qualifier might be "fake", so ignore as needed
          if (isRangePos(t.pos) && name != nme.CONSTRUCTOR) {
            // if the name looks like a desugared line noise name (i.e. $plus for +), just emit the
            // use based on the range positions instead of the text
            val nstr = name.toString
            if (nstr startsWith "$") emitUse("Select", sym, decode(t.pos))
            else emitUse("Select", sym, name.toString, t.pos)
          }
          // else println(s"Skipping Select(name=$name)...")
          if (isRangePos(qual.pos)) traverse(qual)
          // else println(s"Skipping Select(qual=$qual)...")

          // // select can be wacky in two exciting ways; either the qual can be a This which was
          // // magicked up and doesn't exist in the source, or the name can be apply which also
          // // doesn't exist in the source code; yay for fake trees; the way we notice that
          // // something funny is if the qualifier node has the same position as the select node
          // if (t.pos.point == qual.pos.point) {
          //   // if the "qualifier" is a This node, then it's almost certainly synthetic and we want
          //   // to ignore it, but emit the name being selected
          //   if (qual.isInstanceOf[This]) emitUse("Select", sym, name.toString, t.pos)
          //   // otherwise the qualifier is probably valid and its the name that's synthetic (i.e.
          //   // 'apply' or the weird way scala ASTs represent a constructor), so we don't emit a use
          //   // for the name but we do traverse the qualifier
          //   else if (name == nme.apply || name == nme.CONSTRUCTOR) super.traverse(t)
          //   // if the name's not 'apply', then we're lost at sea...
          //   else println(s"Mystery coincident Select($qual, $name) (sel=${decode(t.pos)} qual=${decode(qual.pos)})")
          // } else {
          //   super.traverse(t)
          //   emitUse("Select", sym, name.toString, t.pos)
          // }

          // // this may be an implicit apply, in which case ignore it
          // if (t.pos.point != qual.pos.point) emitUse("Select", sym, name.toString, t.pos)
          // else println(s"Dropping implicit Select($qual, $name) (${t.pos.point} ${qual.pos.point})")
        }
      }

      case t @ Apply(fun, args) => {
        println("----------------------")
        println(s"${showRaw(t)} ${decode(t.pos)}")
        println(t)
        super.traverse(t)
      }

      case t @ This(qual) => {
        val pos = decode(t.pos)
        println(s"This($qual)")
        // if the start/end is more than 4 characters, then this is a qualified this (i.e.
        // Foo.this); yes, the Scala parser doesn't parse that as a Select(), wtf?
        val name = if (pos.end-pos.start == 4) "this" else s"$qual.this"
        // TODO: we should really emit two uses: one for 'Qual' and one for 'this' and the dot
        // should not be part of anything...
        emitUse("This", t.symbol, name, t.pos)
      }

      case tt :TypeTree => {
        val sym = tt.symbol
        println(s"${showRaw(tt)} (${_ignores(tt)})")
        if (!_ignores(tt)) tt.original match {
          case null => emitUse("TypeTree", sym, decode(tt.pos))
          case orig => traverse(orig)
        }
      }

      case _ =>
        val sym = tree.symbol
        if (sym == null || !isIgnored(sym)) {
          println("TODO " + tree.getClass)
          super.traverse(tree)
        }
    }

    private def isIgnored (sym :Symbol) = (!sym.exists ||
                                           sym.isImplementationArtifact ||
                                           sym.isImplClass)

    // trims "(s: String, c: Int)Seq[String]" to "(String,Int)Seq[String]"
    private def trimArgs (args :String) =
      args.replaceAll("\\([^:]+:", "(").replaceAll(",[^:]+:", ",").replaceAll(" ", "")

    private def kind (sym :Symbol) :CKind = sym match {
      case tsym :TypeSymbol => if (sym.hasPackageFlag || sym.isModule) CKind.MODULE else CKind.TYPE
      case msym :MethodSymbol => if (sym.isGetter || sym.isSetter) CKind.VALUE else CKind.FUNC
      case _ => CKind.VALUE // TODO
    }

    private def nameFromSym (sym :Symbol) :String = {
      def isMethNArgs (sym :Symbol) =
        sym.isMethod && !sym.asInstanceOf[MethodSymbolApi].paramLists.isEmpty
      sym.nameString + (if (isMethNArgs(sym)) trimArgs(sym.tpe.toString) else "")
    }

    private def ref (sym :Symbol) :Ref.Global = {
      if (sym.isRoot || sym.isEmptyPackageClass) Ref.Global.ROOT
      // TODO: coalesce all packages above this one; Scala treats each package in the path as a
      // separate symbol "java util function Foo" but we want all the packages to be mashed
      // together "java.util.function Foo"
      else if (sym.hasPackageFlag) Ref.Global.ROOT.plus(pkgName(sym))
      else if (sym == NoSymbol) Ref.Global.ROOT.plus("!invalid!")
      else ref(sym.owner).plus(nameFromSym(sym))
    }

    private def pkgName (sym :Symbol) :String = {
      val osym = sym.owner
      if (osym.isRoot || osym.isEmptyPackageClass) sym.nameString
      else s"${pkgName(osym)}.${sym.nameString}"
    }

    case class Pos (offset :Int, start :Int, end :Int)
    private def decode (pos :Position) :Pos = pos match {
      case NoPosition        => Pos(-1, -1, -1)
      case rp :RangePosition => Pos(rp.point, rp.start, rp.end)
      case _                 => Pos(pos.point, -1, -1)
    }
    private def isRangePos (pos :Position) = pos.isInstanceOf[RangePosition]

    private def access (mods :Modifiers) =
      if (mods hasFlag Flags.PROTECTED) Access.PROTECTED
      else if (mods hasFlag Flags.PRIVATE) Access.PRIVATE
      else Access.PUBLIC

    private def withTree (id :String, tree :Tree)(block : =>Unit) {
      val opos = _curpos
      _curpos = decode(tree.pos)
      _id = _id.plus(id)
      block
      _id = _id.parent
      _curpos = opos
    }

    private def emitSig (tree :Tree, writer :Writer) = {
      val buffer = new StringWriter()
      val printer = new SigTreePrinter(buffer)
      printer.print(tree)
      writer.emitSig(buffer.toString)
      printer.uses foreach { _.emit(writer) }
    }

    case class SigUse (target :Ref.Global, name :String, kind :CKind, offset :Int) {
      def emit (writer :Writer) :Unit = writer.emitSigUse(target, kind, offset, name)
    }

    // used to generate signatures
    class SigTreePrinter (buf :StringWriter)
        extends global.CompactTreePrinter(new PrintWriter(buf)) {
      val uses = ArrayBuffer[SigUse]()

      override def printTree (tree :Tree) = tree match {
        case cd @ ClassDef(mods, name, tparams, impl) =>
          printAnnotations(cd)
          printModifiers(tree, mods)
          val word =
            if (mods.isTrait) "trait"
            else if (tree.symbol.isModuleClass) "object"
            else "class"
          print(word, " ")
          printName(symName(tree, name), tree)
          printTypeParams(tparams)
          // print(if (mods.isDeferred) " <: " else " extends ", impl)

        case tt: TypeTree =>
          // TODO: we need to descend into the type to at least get type applies; also we want to
          // omit the package qualifiers which this seems to include for non-Scala types?
          if ((tree.tpe eq null) || (printPositions && tt.original != null)) {
            if (tt.original != null) print("<type: ", tt.original, ">")
            else print("<type ?>")
          } else if ((tree.tpe.typeSymbol ne null) && tree.tpe.typeSymbol.isAnonymousClass) {
            printName(tree.tpe.typeSymbol.toString, tree)
          } else {
            printName(tree.tpe.toString, tree)
          }

        case _ => super.printTree(tree)
      }

      override protected def printPackageDef(tree: PackageDef, separator: String) = {
        val PackageDef(packaged, stats) = tree
        // printAnnotations(tree)
        print("package ", packaged)
        // printColumn(stats, " {", separator, "}")
      }

      override protected def printValDef (tree :ValDef, resultName : =>String)
                                         (printTypeSignature: => Unit)(printRhs : =>Unit) = {
        super.printValDef(tree, resultName)(printTypeSignature)(())
      }

      override protected def printDefDef (tree :DefDef, resultName : =>String)
                                         (printTypeSignature : =>Unit)(printRhs : =>Unit) = {
        val DefDef(mods, name, tparams, vparamss, tp, rhs) = tree
        printAnnotations(tree)
        printModifiers(tree, mods)
        print(if (tree.symbol.isStable) "val " else "def ")
        printName(resultName, tree)
        printTypeParams(tparams)
        vparamss foreach {printValueParams(_)}
        printTypeSignature
      }

      private def printName (name :String, tree :Tree) {
        uses += SigUse(ref(tree.symbol), name, kind(tree.symbol), pos)
        print(name)
      }

      override def printParam(tree: Tree) = tree match {
        case vd @ ValDef(mods, name, tp, rhs) =>
          printPosition(tree)
          printAnnotations(vd)
          printName(symName(tree, name), tree); printOpt(": ", tp); printOpt(" = ", rhs)
        case TypeDef(mods, name, tparams, rhs) =>
          printPosition(tree)
          printName(symName(tree, name), tree)
          printTypeParams(tparams); print(rhs)
      }

      override def printFlags (flags :Long, privateWithin :String) {
        import Flags._
        val fflags = flags & (CASE|ABSTRACT|PRIVATE|PROTECTED) // TODO: anything else
        super.printFlags(fflags, privateWithin)
      }

      private def pos = buf.getBuffer.length
    }
  }
}
