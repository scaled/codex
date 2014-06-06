//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.extract;

import codex.extract.Utils.*;
import codex.model.*;
import com.sun.source.tree.*;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static codex.extract.Utils.*;

public class ExtractingScanner extends TreePathScanner<Void,Writer> {

  public ExtractingScanner (Types types) {
    _types = types;
  }

  public void extract (Tree ast, Writer writer) throws IOException {
    JCCompilationUnit unit = (JCCompilationUnit)ast;
    writer.openUnit(uriToSource(unit.sourcefile.toUri()));
    _text = unit.sourcefile.getCharContent(true).toString();
    scan(ast, writer);
    writer.closeUnit();
  }

  @Override public Void visitCompilationUnit (CompilationUnitTree node, Writer writer) {
    JCCompilationUnit unit = (JCCompilationUnit)node;
    _unit.push(unit);
    String pname = unit.packge.toString();
    _id = _id.plus(pname);
    int offset = _text.indexOf(pname, unit.pos);
    writer.openDef(_id, pname, Kind.MODULE, Flavor.NONE, true, offset, 0, _text.length());
    writer.emitSig(pname);
    writer.commitDef();
    super.visitCompilationUnit(node, writer);
    writer.closeDef();
    _id = _id.parent;
    _unit.pop();
    return null;
  }

  @Override public Void visitClass (ClassTree node, Writer writer) {
    _symtab.push(new HashMap<>());
    JCClassDecl tree = (JCClassDecl)node;
    _class.push(tree);
    String name = tree.name.toString();
    boolean isAnon = name == "";
    String clid = name + (isAnon ? ("$" + nextanon()) : "");

    String cname;
    if (!isAnon) cname = clid;
    else if (tree.extending != null) cname = tree.extending.toString();
    else cname = tree.implementing.toString();

    Flavor flavor;
    if (hasFlag(tree.mods, Flags.ANNOTATION)) flavor = Flavor.ANNOTATION;
    else if (hasFlag(tree.mods, Flags.ENUM)) flavor = Flavor.ENUM;
    else if (hasFlag(tree.mods, Flags.INTERFACE)) flavor = Flavor.INTERFACE;
    else if (hasFlag(tree.mods, Flags.ABSTRACT)) flavor = Flavor.ABSTRACT_CLASS;
    else flavor = Flavor.CLASS;

    // TODO: improve approach to finding position of class name
    int treeStart = tree.getStartPosition();
    int start = _text.indexOf(cname, treeStart);

    int ocount = _anoncount;
    _anoncount = 0;
    _id = _id.plus(clid);

    // we allow the name to be "" for anonymous classes so that they can be properly filtered
    // in the user interface; we eventually probably want to be more explicit about this
    writer.openDef(_id, name, Kind.TYPE, flavor, isPub(tree.mods.flags), start,
                   treeStart, tree.getEndPosition(_unit.peek().endPositions));

    // emit supertype relations
    Type t = tree.type;
    if (t != null) {
      Type st = _types.supertype(t);
      Ref.Global stgt = targetForTypeSym(_types.erasure(st).tsym);
      writer.emitRelation(Relation.INHERITS, stgt);
      writer.emitRelation(Relation.SUPERTYPE, stgt);
      for (Type it : _types.interfaces(t)) writer.emitRelation(
        Relation.SUPERTYPE, targetForTypeSym(_types.erasure(it).tsym));
    }

    // name in anon classes is "", but for signature generation we want to replace it with the
    // name that will be later assigned by the compiler EnclosingClass$N
    new SigPrinter(_id, tree.name.table.fromString(clid)) {
      @Override public void printAnnotations (List<JCAnnotation> trees) {
        super.printAnnotations(trees);
        try {
          if (!trees.isEmpty()) println();
        } catch (IOException ioe) {
          ioe.printStackTrace(System.err);
        }
      }
    }.emit(tree, writer);

    // emit docs
    _doc.push(findDoc(treeStart).emit(writer));

    writer.commitDef();
    super.visitClass(node, writer);
    writer.closeDef();

    _anoncount = ocount;
    _id = _id.parent;
    _doc.pop();
    _symtab.pop();
    _class.pop();
    return null;
  }

  @Override public Void visitMethod (MethodTree node, Writer writer) {
    JCMethodDecl tree = (JCMethodDecl)node;
    // don't emit a def for synthesized ctors
    if (!hasFlag(tree.mods, Flags.GENERATEDCONSTR)) {
      _symtab.push(new HashMap<>());
      _meth.push(tree);

      boolean isCtor = (tree.name == tree.name.table.names.init);
      Flavor flavor;
      if (isCtor) flavor = Flavor.CONSTRUCTOR;
      else if (hasFlag(_class.peek().mods, Flags.INTERFACE) ||
               hasFlag(tree.mods, Flags.ABSTRACT)) flavor = Flavor.ABSTRACT_METHOD;
      else if (hasFlag(tree.mods, Flags.STATIC)) flavor = Flavor.STATIC_METHOD;
      else flavor = Flavor.METHOD;

      // interface methods are specially defined to always be public
      boolean isPub = hasFlag(_class.peek().mods.flags, Flags.INTERFACE) || isPub(tree.mods.flags);
      String name = isCtor ? _class.peek().name.toString() : tree.name.toString();

      // the id for a method includes signature information
      String methid = (tree.type == null) ? "" : tree.type.toString();
      _id = _id.plus(name + methid);

      int treeStart = tree.getStartPosition();
      int offset = _text.indexOf(name, treeStart);
      writer.openDef(_id, name, Kind.FUNC, flavor, isPub, offset,
                     treeStart, tree.getEndPosition(_unit.peek().endPositions));

      if (tree.sym != null) emitSuperMethod(tree.sym, writer);

      new SigPrinter(_id, _class.peek().name).emit(tree, writer);

      DefDoc doc = findDoc(treeStart);
      doc.emit(writer);
      _doc.push(doc);
      writer.commitDef();

      writer.commitDef();
      super.visitMethod(node, writer);
      writer.closeDef();

      _doc.pop();
      _id = _id.parent;
      _meth.pop();
      _symtab.pop();
    }
    return null;
  }

  @Override public Void visitTypeParameter (TypeParameterTree node, Writer writer) {
    JCTypeParameter tree = (JCTypeParameter)node;

    String name = node.getName().toString();
    int offset = tree.getStartPosition();
    _id = _id.plus(name);
    writer.openDef(_id, name, Kind.TYPE, Flavor.TYPE_PARAM, true,
                   offset, offset, offset + name.length());

    // make super the erased type(s) of the tvar (TODO: handle intersection types)
    Type stype = _types.erasure(tree.type);
    writer.emitRelation(Relation.SUPERTYPE, targetForTypeSym(stype.tsym));

    new SigPrinter(_id, null).emit(tree, writer);

    // see if we have "@param <T>" style documentation for this type parameter
    DefDoc curdoc = _doc.peek();
    if (curdoc != null) curdoc.emitParam("<" + name + ">", writer);

    writer.commitDef();
    super.visitTypeParameter(node, writer);
    writer.closeDef();

    _id = _id.parent;
    return null;
  }

  @Override public Void visitBlock (BlockTree node, Writer writer) {
    _symtab.push(new HashMap<>());
    super.visitBlock(node, writer);
    _symtab.pop();
    return null;
  }

  @Override public Void visitVariable (VariableTree node, Writer writer) {
    JCVariableDecl tree = (JCVariableDecl)node;
    Flavor flavor;
    boolean isField = _meth.peek() == null;
    boolean isParam = hasFlag(tree.mods, Flags.PARAMETER);
    if (isField) flavor = hasFlag(tree.mods, Flags.STATIC) ? Flavor.STATIC_FIELD : Flavor.FIELD;
    else flavor = isParam ? Flavor.PARAM : Flavor.LOCAL;
    boolean isPub = isField && isPub(tree.mods.flags);

    String name = tree.name.toString();
    _id = _id.plus(name);

    int varend = tree.vartype.getEndPosition(_unit.peek().endPositions);
    int start = _text.indexOf(name, varend);
    int treeStart = tree.getStartPosition();
    int bodyStart = (treeStart == -1) ? start : treeStart;

    // add a symtab mapping for this vardef
    if (tree.sym != null) _symtab.peek().put(tree.sym, _id);

    writer.openDef(_id, name, Kind.VALUE, flavor, isPub, start,
                   bodyStart, tree.getEndPosition(_unit.peek().endPositions));

    // emit our signature
    new SigPrinter(_id, _class.peek().name).emit(tree, writer);

    // if this is a field, it will have its own doc
    if (isField) findDoc(treeStart).emit(writer);
    // otherwise try to extract its documentation from the method javadoc
    else if (isParam) _doc.peek().emitParam(name, writer);

    writer.commitDef();
    // if this is an enum field, don't call super visit as that will visit a bunch of synthetic
    // mishmash which we don't want to emit defs for
    if (!hasFlag(tree.mods, Flags.ENUM)) super.visitVariable(node, writer);
    writer.closeDef();

    _id = _id.parent;
    return null;
  }

  @Override public Void visitIdentifier (IdentifierTree node, Writer writer) {
    JCIdent tree = (JCIdent)node;
    if (_class.peek() == null || // make sure we're not looking at an import
        tree.sym == null || inAnonExtendsOrImplements() ||
        hasFlag(tree.sym.flags(), Flags.SYNTHETIC) || isSynthSuper(tree)) return null;

    // if this identifier is the "C" part of a "new C" expression, we want to climb up the AST
    // and get the constructor from our parent tree node
    Tree pnode = getCurrentPath().getParentPath().getLeaf();
    Symbol tsym;
    if (pnode.getKind() == Tree.Kind.NEW_CLASS) {
      JCNewClass ptree = (JCNewClass)pnode;
      Symbol csym = ptree.constructor;
      // a JCNewClass has a number of JCIdent direct descendents, including the constructor
      // arguments; we want to be sure that we're looking at the 'clazz' descendent
      if (tree != ptree.clazz) tsym = tree.sym;
      // if the ctor type could not be resolved, just use the type itself (it probably won't
      // have been resolved either, but we'll cope with that later)
      else if (csym == null) tsym = tree.sym;
      // if this is an anonymous class constructor...
      else if (csym.owner.name == csym.owner.name.table.names.empty) {
        // TODO: if the parent type is an interface, there will be no constructor (and it
        // would be weird to link to the zero-argument Object constructor), but if the type
        // is a class, it would be nice to link to the appropriate constructor
        // val ptype = _types.supertype(csym.owner.`type`)
        // TODO: if supertype is not Object, find and use super ctor
        tsym = tree.sym; // for now, target the type itself
      }
      // otherwise all is well, so use the constructor symbol as our target
      else tsym = csym;
    } else tsym = tree.sym;

    writer.emitUse(targetForSym(tree.name, tsym), tree.name.toString(), kindForSym(tree.sym),
                   tree.getStartPosition());
    return null;
  }

  @Override public Void visitMemberSelect (MemberSelectTree node, Writer writer) {
    super.visitMemberSelect(node, writer);
    JCFieldAccess tree = (JCFieldAccess)node;
    // make sure we're not looking at an import
    if (_class.peek() != null && tree.sym != null) {
      String name = tree.name.toString();
      // TODO: is there a better way to get the start position of the selected name?
      int selend = tree.getStartPosition() + tree.selected.toString().length();
      int offset = _text.indexOf(name, selend);
      // TODO: there's some weirdness with lambdas here: when we see (f -> f...) the use of f has a
      // tree name that is the fully qualified type name of f, so if f was java.lang.reflect.Method
      // that's what we see in the AST, rather than something like JCIdentifier(f)
      if (offset == -1) System.err.println(
        String.format("Unable to find use in member select %s (%s @ %d / %d %s)",
                      tree, name, selend, tree.getStartPosition(), tree.selected.toString()));
      else writer.emitUse(targetForSym(name, tree.sym), name, kindForSym(tree.sym), offset);
    }
    return null;
  }

  /** Anonymous classes will emit a use for the supertype of the anonymous class in the block
    * that constructs the class, however the AST is expanded to:
    * {@code foo = new <empty> extends AnonSuper { ... } // or implements AnonSuper { ... }}
    * which will result in a second use for the anonymous supertype, which we want to suppress. */
  private boolean inAnonExtendsOrImplements () {
    Tree tree = getCurrentPath().getParentPath().getLeaf();
    return (tree instanceof JCClassDecl) && (((JCClassDecl)tree).name.toString() == "");
  }

  // the only way to identify a synthesized super() seems to be to check that its source position
  // is the same as the enclosing block, javac helpfully fails to add a SYNTHETIC flag
  private boolean isSynthSuper (JCIdent tree) {
    if (tree.name != tree.name.table.names._super) return false;
    JCBlock block = enclosingBlock(getCurrentPath());
    return (block != null) && (tree.getStartPosition() == block.getStartPosition());
  }

  private JCBlock enclosingBlock (TreePath path) {
    if (path == null) return null;
    else {
      Tree leaf = path.getLeaf();
      if (leaf instanceof JCBlock) return (JCBlock)leaf;
      else return enclosingBlock(path.getParentPath());
    }
  }

  private String pathToString (TreePath path) {
    String pp = (path.getParentPath() == null) ? "" : (pathToString(path.getParentPath()) + ".");
    return pp + path.getLeaf().getKind();
  }

  private Ref.Global targetForSym (Name name, Symbol sym) {
    return targetForSym(name.toString(), sym);
  }

  private Ref.Global targetForSym (String name, Symbol sym) {
    if (sym instanceof VarSymbol) {
      VarSymbol vs = (VarSymbol)sym;
      switch (vs.getKind()) {
      case FIELD:
      case ENUM_CONSTANT:
        return targetForSym("<error>", vs.owner).plus(name);
      // EXCEPTION_PARAMETER, PARAMETER, LOCAL_VARIABLE (all in symtab)
      default:
        for (Map<VarSymbol,Ref.Global> symtab : _symtab) {
          Ref.Global id = symtab.get(vs);
          if (id != null) return id;
        }
        System.err.println("targetForSym: unhandled varsym kind: " + vs.getKind());
        return Ref.Global.ROOT.plus("unknown");
      }
    } else return targetForTypeSym(sym);
  }

  private void emitSuperMethod (MethodSymbol m, Writer writer) {
    TypeSymbol owner = (TypeSymbol)m.owner;
    for (Type sup : _types.closure(owner.type)) {
      if (sup != owner.type) {
        Scope scope = sup.tsym.members();
        Scope.Entry e = scope.lookup(m.name);
        while (e.scope != null) {
          if (e.sym.isStatic() || !m.overrides(e.sym, owner, _types, true)) e = e.next();
          else {
            writer.emitRelation(Relation.OVERRIDES, targetForTypeSym(e.sym));
            // TODO: strictly speaking, this should find all interface methods but currently it
            // stops at the first
            return;
          }
        }
      }
    }
  }

  class DocBit {
    public final int offset;
    public final int length;

    public DocBit (int offset, int length) {
      this.offset = offset;
      this.length = length;
    }
  }

  class DefDoc {
    public final int offset;
    public final int length;
    public final Map<String,DocBit> params = new HashMap<>();
    public List<DeferredWrite> uses = List.nil();

    public DefDoc (int start, int end, String text) {
      this.offset = start;
      this.length = end-start;
      try {
        parseDoc(text);
      } catch (Exception e) {
        e.printStackTrace(System.err);
      }
    }

    public DefDoc emit (Writer writer) {
      writer.emitDoc(offset, length);
      for (DeferredWrite write : uses) write.apply(writer);
      return this;
    }

    /** Emits documentation for an individual parameter, if available. */
    public void emitParam (String param, Writer writer) {
      DocBit pdoc = params.get(param);
      if (pdoc != null) writer.emitDoc(pdoc.offset, pdoc.length);
    }

    /** Performs some primitive extraction from Javadocs. Presently: handles {at code} and strips at
      * tags (at param etc. will be handled later, and you can look at the full source for at
      * author, etc.). */
    private void parseDoc (String text) {
      // first expand brace tag patterns
      Matcher btm = _braceTagPat.matcher(text);
      while (btm.find()) {
        String target = btm.group(2);
        switch (btm.group(1)) {
        case "link":
        case "linkplain":
          Symbol tsym = resolveLink(target);
          if (tsym != null) {
            Ref.Global tid = targetForSym(target, tsym);
            Kind tkind = kindForSym(tsym);
            int start = btm.start(2);
            uses = uses.prepend(w -> w.emitDocUse(tid, target, tkind, start));
          }
          break;
        // TODO: case "value":
        default:
          break; // nada
        }
      }

      // TODO: identify types in @throws and @see and add uses for them

      // // now look for a block tags section and process it
      // Matcher tm = _tagPat.matcher(text);
      // if (tm.find()) {
      //   String preText = text.substring(0, tm.start).trim();
      //   int tstart = tm.start();
      //   int tend = tm.end();
      //   while (tm.find()) {
      //     processTag(etext.substring(tstart, tend), etext.substring(tend, tm.start).trim)
      //     tstart = tm.start
      //     tend = tm.end
      //   }
      //   processTag(etext.substring(tstart, tend), etext.substring(tend).trim)
    }

    // private void addTag (String tag, String text, int start, int end) {
    //   switch (tag) {
    //   case "@exception":
    //   case "@throws" => throws += firstRest(text)
    //   case "@param" => params += firstRest(text)
    //   case "@deprecated" => notes += DocBit("Deprecated", text)
    //   case "@return" => notes += DocBit("Returns", text)
    //   case "@see" => notes += DocBit("See", "<code>" + text + "</code>")
    //   case "@author" | "@serial" | "@serialData" | "@serialField" | "@since"
    //      | "@version" => // noop!
    // }

    private Symbol resolveLink (String text) {
      int hidx = text.indexOf("#");
      if (hidx == -1) {
        return null; // TODO: look up type
      } else {
        String tname = text.substring(0, hidx);
        String mname = text.substring(hidx+1);
        if (hidx == 0) {
          JCClassDecl cc = _class.peek();
          return lookup(cc.sym.members(), cc.name.table.fromString(mname));
        } else {
          return null; // TODO: look up type, then resolve method
        }
      }
    }
  }

  private final DefDoc NO_DOC = new DefDoc(0, 0, "") {
    public DefDoc emit (Writer writer) { return this; } // noop!
    public void emitParam (Writer writer) {} // noop!
  };

  private DefDoc findDoc (int pos) {
    try {
      int docEndPre = _text.lastIndexOf("*/", pos);
      if (docEndPre == -1) return NO_DOC;
      else {
        int docEnd = docEndPre + 2;
        String docToDef = _text.substring(docEnd, pos);
        if (docToDef.trim().length() != 0) return NO_DOC;
        else {
          int commentStart = _text.lastIndexOf("/*", docEnd);
          int docStart = _text.lastIndexOf("/**", docEnd);
          if (docStart != commentStart) return NO_DOC;
          else return new DefDoc(docStart, docEnd, _text.substring(docStart, docEnd));
        }
      }
    } catch (Exception e) {
      System.err.println("Error finding doc at " + pos + " in " + _unit.peek().sourcefile + ":");
      e.printStackTrace(System.err);
      return NO_DOC;
    }
  }

  private Source uriToSource (URI uri) {
    String str = uri.toString();
    String path;
    if (str.startsWith("jar:file:")) path = str.substring("jar:file:".length());
    else if (str.startsWith("zip:file:")) path = str.substring("zip:file:".length());
    else path = uri.getPath();
    return Source.fromString(path);
  }

  private Pattern _starPref = Pattern.compile("^\\* ?");

  private Symbol lookup (Scope scope, Name name) {
    Scope.Entry e = scope.lookup(name);
    return (e.scope == null) ? null : e.sym;
  }

  // TODO: this may need to be a full parser since it may match braces inside an @code block
  private static Pattern _braceTagPat = Pattern.compile(
    "\\{@(code|link|linkplain|literal|value)\\s([^}]*)\\}", Pattern.DOTALL);
  private static Pattern _tagPat = Pattern.compile(
    "@(author|deprecated|exception|param|return|see|serial|serialData|serialField|" +
    "since|throws|version)");

  private boolean hasFlag (JCModifiers mods, long flag) { return (mods.flags & flag) != 0; }
  private boolean hasFlag (long flags, long flag) { return (flags & flag) != 0; }
  private boolean isPub (long flags) { return hasFlag(flags, Flags.PUBLIC); }
  // private def flagsToAccess (flags :Long) =
  //   if () "public"
  //   else if (hasFlag(flags, Flags.PROTECTED)) "protected"
  //   else if (hasFlag(flags, Flags.PRIVATE)) "private"
  //   else "default"

  public int nextanon () { _anoncount += 1; return _anoncount; }
  private int _anoncount = 0;

  private Deque<JCCompilationUnit> _unit = new ArrayDeque<>();
  private Deque<JCClassDecl> _class = new ArrayDeque<>();
  private Deque<JCMethodDecl> _meth = new ArrayDeque<>();
  private Deque<DefDoc> _doc = new ArrayDeque<>();

  private Deque<Map<VarSymbol,Ref.Global>> _symtab = new ArrayDeque<>();
  private Ref.Global _id = Ref.Global.ROOT;
  private String _text;

  private final Types _types;
}
