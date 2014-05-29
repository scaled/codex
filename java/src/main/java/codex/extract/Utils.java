//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.extract;

import codex.model.Kind;
import com.google.common.collect.Sets;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Flags;
// import com.sun.tools.javac.code.{Flags, Scope, Symbol, Type, Types, TypeTags}
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.Pretty;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Set;

public class Utils {

  public static final String LINE_SEP = System.getProperty("line.separator");

  public static final Set<String> IGNORED_ANNS = Sets.newHashSet("Override", "SuppressWarnings");

  public static Kind kindForSym (Symbol sym) {
    if (sym instanceof ClassSymbol || sym instanceof TypeSymbol) return Kind.TYPE;
    else if (sym instanceof MethodSymbol) return Kind.FUNC;
    else if (sym instanceof VarSymbol) return Kind.TERM;
    else throw new IllegalArgumentException("Unknown kind for " + sym);
  }

  // note the more general targetForSym in the tree traverser which can handle local names; this
  // can only handle type names, which is fine for handling targets in docs and signatures
  public static List<String> targetForTypeSym (Symbol sym) {
    if (sym == null) {
      return List.of(""); // the "root" type's owner; nothing to see here, move it along
    }
    else if (sym instanceof ClassSymbol) {
      return targetForTypeSym(sym.owner).prepend(sym.name.toString());
    }
    else if (sym instanceof PackageSymbol) {
      return List.of(sym.toString()); // keep the dots between packages
    }
    else if (sym instanceof TypeSymbol) {
      return targetForTypeSym(sym.owner).prepend(""+sym.name); // type param
    }
    else if (sym instanceof MethodSymbol) {
      Name mname = (sym.name == sym.name.table.names.init) ? sym.owner.name : sym.name;
      return targetForTypeSym(sym.owner).prepend(""+mname+sym.type);
    } else {
      System.err.println("Unhandled type sym " + sym.getClass() + " '" + sym + "'");
      return List.of(sym.name.toString());
    }
  }

  public static String joinDefIds (String first, String second) {
    return first + (first.isEmpty() ? "" : " ") + second;
  }

  public interface DeferredWrite {
    void apply (Writer writer);
  }

  public static class SigPrinter extends Pretty {
    public SigPrinter (List<String> id, Name enclClassName) {
      this(new StringWriter(), id, enclClassName);
    }

    public SigPrinter (StringWriter out, List<String> id, Name enclClassName) {
      super(out, false);
      _out = out;
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
    private final List<String> _id;
    private final Name _enclClassName;
    private List<DeferredWrite> _writes = List.nil();
    private boolean _nested = false;

    @Override public void printBlock (List<? extends JCTree> stats) {} // noop!
    @Override public void printEnumBody (List<JCTree> stats) {} // noop!
    @Override public void printAnnotations (List<JCAnnotation> trees) {
      try {
        while (trees.nonEmpty()) {
          int olen = _out.getBuffer().length();
          printStat(trees.head);
          // if the annotation wasn't ommitted, add a space after it
          if (_out.getBuffer().length() > olen) print(" ");
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
        printFlags(tree.mods.flags & ~Flags.INTERFACE);
        if ((tree.mods.flags & Flags.INTERFACE) != 0) {
          print("interface " + _enclClassName);
          cpos = _out.getBuffer().length() - _enclClassName.toString().length();
          printTypeParameters(tree.typarams);
          if (tree.implementing.nonEmpty()) {
            print(" extends ");
            printExprs(tree.implementing);
          }
        } else {
          if ((tree.mods.flags & Flags.ENUM) != 0) print("enum " + _enclClassName);
          else print("class " + _enclClassName);
          cpos = _out.getBuffer().length() - _enclClassName.toString().length();
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
        addSigDef(_id, _enclClassName.toString(), Kind.TYPE, cpos);
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
          // TEMP: try life without modifiers
          // printExpr(tree.mods)
          // type parameters are now extracted into separate defs
          // printTypeParameters(tree.typarams)
          if (!isCtor) {
            printExpr(tree.restype);
            print(" ");
          }
          int mpos = _out.getBuffer().length();
          String mname = isCtor ? _enclClassName.toString() : tree.name.toString();
          print(mname);
          print("(");
          printExprs(tree.params);
          print(")");
          // omit throws from signatures
          // if (tree.thrown.nonEmpty()) {
          //   print("\n  throws ");
          //   printExprs(tree.thrown);
          // }
          if (tree.defaultValue != null) {
            print(" default ");
            printExpr(tree.defaultValue);
          }
          _nested = false;
          addSigDef(_id, mname, Kind.FUNC, mpos);
        }
      } catch (IOException ioe) {
        ioe.printStackTrace(System.err);
      }
    }

    @Override public void visitTypeParameter (JCTypeParameter tree) {
      // note the start of the buffer as that's where the type parameter appears
      int tpos = _out.getBuffer().length();
      // now format the type parameter expression (which may be T extends Foo, etc.)
      super.visitTypeParameter(tree);
      // if we're generating the signature for a class, we need to append the type param name to id
      // to get our id, otherwise id is our id as is
      String name = tree.name.toString();
      addSigDef((_enclClassName != null) ? _id.prepend(name) : _id, name, Kind.TYPE, tpos);
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
        int vpos = 0;
        String name = tree.name.toString();
        if (_nested) {
          JCExpression oinit = tree.init;
          tree.init = null;
          super.visitVarDef(tree);
          tree.init = oinit;
          vpos = _out.getBuffer().length()-name.length();
        } else {
          vpos = _out.getBuffer().length();
          printExpr(tree.vartype);
          print(" " + name);
        }
        // we're either printing the sig for a plain old vardef, or we're nested, in which case we're
        // printing the signature for a method, but it has parameters, and 'id' is the method id, so
        // we need to append the var name to get the var def id
        addSigDef(_nested ? _id.prepend(name) : _id, name, Kind.TERM, vpos);
      } catch (IOException ioe) {
        ioe.printStackTrace(System.err);
      }
    }

    @Override public void visitIdent (JCIdent tree) {
      if (tree.sym != null) {
        addSigUse(targetForTypeSym(tree.sym), tree.name.toString(), kindForSym(tree.sym),
                  _out.getBuffer().length());
      }
      super.visitIdent(tree);
    }

    private void addSigDef (Collection<String> id, String name, Kind kind, int offset) {
      _writes = _writes.prepend(w -> w.emitSigDef(id, name, kind, offset));
    }

    private void addSigUse (Collection<String> target, String name, Kind kind, int offset) {
      _writes = _writes.prepend(w -> w.emitSigUse(target, name, kind, offset));
    }
  }

}
