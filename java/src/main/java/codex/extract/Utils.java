//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.extract;

import codex.model.Kind;
import codex.model.Ref;
import com.google.common.collect.Sets;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.Pretty;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Set;
import javax.lang.model.element.ElementKind;

public class Utils {

  public static final String LINE_SEP = System.getProperty("line.separator");

  public static final Set<String> IGNORED_ANNS = Sets.newHashSet("Override", "SuppressWarnings");

  public static Kind kindForSym (Symbol sym) {
    if (sym instanceof PackageSymbol) return Kind.MODULE;
    else if (sym instanceof ClassSymbol || sym instanceof TypeSymbol) return Kind.TYPE;
    else if (sym instanceof MethodSymbol) return Kind.FUNC;
    else if (sym instanceof VarSymbol) return Kind.VALUE;
    else throw new IllegalArgumentException("Unknown kind for " + sym);
  }

  // note the more general targetForSym in the tree traverser which can handle local names; this
  // can only handle type names, which is fine for handling targets in docs and signatures
  public static Ref.Global targetForTypeSym (Symbol sym) {
    if (sym == null) {
      return Ref.Global.ROOT; // the "root" type's owner; nothing to see here, move it along
    }
    else if (sym instanceof ClassSymbol) {
      ClassSymbol csym = (ClassSymbol)sym;
      // TODO: use csym.classfile and csym.sourcefile to determine project for this symbol
      return targetForTypeSym(sym.owner).plus(sym.name.toString());
    }
    else if (sym instanceof PackageSymbol) {
      return Ref.Global.ROOT.plus(sym.toString()); // keep the dots between packages
    }
    else if (sym instanceof TypeSymbol) {
      return targetForTypeSym(sym.owner).plus(""+sym.name); // type param
    }
    else if (sym instanceof MethodSymbol) {
      Name mname = (sym.name == sym.name.table.names.init) ? sym.owner.name : sym.name;
      return targetForTypeSym(sym.owner).plus(""+mname+sym.type);
    }
    else if (sym instanceof VarSymbol) {
      // we can encounter a var symbol when our chain of parents exits an anonymous class and rises
      // up into the field or variable to which the class was assigned
      return targetForTypeSym(sym.owner).plus(sym.name.toString());
    } else {
      System.err.println("Unhandled type sym " + sym.getClass() + " '" + sym + "'");
      return Ref.Global.ROOT.plus(sym.name.toString());
    }
  }

  public static String joinDefIds (String first, String second) {
    return first + (first.isEmpty() ? "" : " ") + second;
  }

  public interface DeferredWrite {
    void apply (Writer writer);
  }

  public static class SigPrinter extends Pretty {
    public SigPrinter (Ref.Global id, Name enclClassName) {
      this(new StringWriter(), id, enclClassName);
    }

    public SigPrinter (StringWriter out, Ref.Global id, Name enclClassName) {
      super(out, false);
      _out = out;
      _buf = _out.getBuffer();
      _id = id;
      _enclClassName = enclClassName;
    }

    public void emit (JCTree tree, Writer writer) {
      try {
        printExpr(tree);
      } catch (IOException ioe) {
        ioe.printStackTrace(System.err);
      }
      String sig = _out.toString();
      // filter out the wacky crap Pretty puts in for enums
      sig = sig.trim().replace("/*public static final*/ ", "");
      writer.emitSig(sig);
      for (DeferredWrite dw : _writes) dw.apply(writer);
    }

    private final StringWriter _out;
    private final StringBuffer _buf;
    private final Ref.Global _id;
    private final Name _enclClassName;
    private List<DeferredWrite> _writes = List.nil();
    private boolean _nested = false;

    @Override public void printFlags (long flags) throws IOException {
      // omit some flags from printing
      super.printFlags(flags & ~Flags.INTERFACE & ~Flags.PUBLIC & ~Flags.PROTECTED &
                       ~Flags.PRIVATE);
    }

    @Override public void printBlock (List<? extends JCTree> stats) {} // noop!
    @Override public void printEnumBody (List<JCTree> stats) {} // noop!
    @Override public void printAnnotations (List<JCAnnotation> trees) {
      try {
        while (trees.nonEmpty()) {
          int olen = _buf.length();
          printStat(trees.head);
          // if the annotation wasn't ommitted, add a space after it
          if (_buf.length() > olen) print(" ");
          trees = trees.tail;
        }
      } catch (IOException ioe) {
        ioe.printStackTrace(System.err);
      }
    }

    @Override public void visitClassDef (JCClassDecl tree) {
      try {
        int cpos = 0;
        printAnnotations(tree.mods.annotations);
        printFlags(tree.mods.flags);
        if ((tree.mods.flags & Flags.INTERFACE) != 0) {
          print("interface " + _enclClassName);
          cpos = _buf.length() - _enclClassName.toString().length();
          printTypeParameters(tree.typarams);
          if (tree.implementing.nonEmpty()) {
            print(" extends ");
            printExprs(tree.implementing);
          }
        } else {
          if ((tree.mods.flags & Flags.ENUM) != 0) print("enum " + _enclClassName);
          else print("class " + _enclClassName);
          cpos = _buf.length() - _enclClassName.toString().length();
          printTypeParameters(tree.typarams);
          if (tree.extending != null) {
            print(" extends ");
            printExpr(tree.extending);
          }
          if (tree.implementing.nonEmpty()) {
            print(" implements ");
            printExprs(tree.implementing);
          }
        }
        addSigUse(_id, _enclClassName.toString(), Kind.TYPE, cpos);
      } catch (IOException ioe) {
        ioe.printStackTrace(System.err);
      }
    }

    @Override public void visitMethodDef (JCMethodDecl tree) {
      try {
        // only print non-anonymous constructors
        boolean isCtor = tree.name == tree.name.table.names.init;
        if (!isCtor || _enclClassName != null) {
          _nested = true;
          printExpr(tree.mods);
          // type parameters are now extracted into separate defs
          // printTypeParameters(tree.typarams)
          if (!isCtor) {
            printExpr(tree.restype);
            print(" ");
          }
          int mpos = _buf.length();
          String mname = isCtor ? _enclClassName.toString() : tree.name.toString();
          print(mname);
          print("(");
          printExprs(tree.params);
          print(")");
          if (tree.thrown.nonEmpty()) {
            print("\n  throws ");
            printExprs(tree.thrown);
          }
          if (tree.defaultValue != null) {
            print(" default ");
            printExpr(tree.defaultValue);
          }
          _nested = false;
          addSigUse(_id, mname, Kind.FUNC, mpos);
        }
      } catch (IOException ioe) {
        ioe.printStackTrace(System.err);
      }
    }

    @Override public void visitTypeParameter (JCTypeParameter tree) {
      // note the start of the buffer as that's where the type parameter appears
      int tpos = _buf.length();
      // now format the type parameter expression (which may be T extends Foo, etc.)
      super.visitTypeParameter(tree);
      // if we're generating the signature for a class, we need to append the type param name to id
      // to get our id, otherwise id is our id as is
      String name = tree.name.toString();
      addSigUse((_enclClassName != null) ? _id.plus(name) : _id, name, Kind.TYPE, tpos);
    }

    @Override public void visitTypeIdent (JCPrimitiveTypeTree tree) {
      int tpos = _buf.length();
      super.visitTypeIdent(tree);
      String name = _buf.substring(tpos, _buf.length());
      // this use will not resolve to anything, but at least we'll have a properly kinded use for
      // this primitive type which will make code colorization work uniformly
      addSigUse(Ref.Global.ROOT.plus(name), name, Kind.TYPE, tpos);
    }

    @Override public void visitAnnotation (JCAnnotation tree) {
      try {
        // we skip override annotations in our signatures
        if (!IGNORED_ANNS.contains(tree.annotationType.toString())) {
          print("@");
          printExpr(tree.annotationType);
          if (tree.args != null && !tree.args.isEmpty()) {
            print("(");
            printExprs(tree.args);
            print(")");
          }
        }
      } catch (IOException ioe) {
        ioe.printStackTrace(System.err);
      }
    }

    @Override public void visitVarDef (JCVariableDecl tree) {
      try {
        String name = tree.name.toString();
        if (_nested) {
          JCExpression oinit = tree.init;
          tree.init = null;
          super.visitVarDef(tree);
          tree.init = oinit;
        } else {
          printExpr(tree.vartype);
          print(" " + name);
        }
        int vpos = _buf.length()-name.length();
        // we're either printing the sig for a plain old vardef, or we're nested, in which case
        // we're printing the signature for a method, but it has parameters, and 'id' is the method
        // id, so we need to append the var name to get the var def id
        addSigUse(_nested ? _id.plus(name) : _id, name, Kind.VALUE, vpos);
      } catch (IOException ioe) {
        ioe.printStackTrace(System.err);
      }
    }

    @Override public void visitIdent (JCIdent tree) {
      if (tree.sym != null) {
        addSigUse(targetForTypeSym(tree.sym), tree.name.toString(), kindForSym(tree.sym),
                  _buf.length());
      }
      super.visitIdent(tree);
    }

    private void addSigUse (Ref.Global target, String name, Kind kind, int offset) {
      _writes = _writes.prepend(w -> w.emitSigUse(target, name, kind, offset));
    }
  }
}
