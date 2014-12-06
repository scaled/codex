//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.extract;

import java.io.PrintWriter;
import codex.model.*;
import java.util.Collection;

/**
 * A {@code Writer} implementation that emits a compact text-based representation of the metadata.
 */
public class DebugWriter extends Writer {

  public DebugWriter (PrintWriter out, String source) {
    _out = out;
    _source = source;
  }

  @Override public void openSession () {
    // nada
  }

  @Override public void openUnit (Source source) {
    emit("unit", "src", source);
    _indent += 1;
  }

  @Override public void openDef (Ref.Global id, String name, Kind kind, Flavor flavor,
                                 boolean exported, Access access,
                                 int offset, int bodyStart, int bodyEnd) {
    emit("def", "name", name, "kind", kind, "flavor", flavor, "exp", exported, "access", access,
         "off", offset, "start", bodyStart, "end", bodyEnd, "id", id);
    checkName("def", offset, name);
    _indent += 1;
  }

  @Override public void emitRelation (Relation relation, Ref.Global target) {
    emit("rel", "type", relation, "tgt", target);
  }

  @Override public void emitSig (String text) {
    emit("sig", "text", text);
  }
  @Override public void emitSigUse (Ref.Global target, Kind kind, int offset, int length) {
    emit("siguse", "tgt", target, "kind", kind, "off", offset, "len", length);
  }

  @Override public void emitDoc (int offset, int length) {
    emit("doc", "off", offset, "len", length);
  }
  @Override public void emitDocUse (Ref.Global target, Kind kind, int offset, int length) {
    emit("docuse", "tgt", target, "kind", kind, "off", offset, "len", length);
  }

  @Override public void emitUse (Ref.Global target, Kind kind, int offset, String name) {
    emit("use", "tgt", target, "kind", kind, "off", offset, "name", name);
    checkName("use", offset, name);
  }
  @Override public void emitUse (Ref.Global target, Kind kind, int offset, int length) {
    emit("use", "tgt", target, "kind", kind, "off", offset, "len", length);
  }

  @Override public void closeDef () {
    _indent -= 1;
  }
  @Override public void closeUnit () {
    _indent -= 1;
  }
  @Override public void closeSession () {
    // nada
  }

  private void checkName (String whence, int offset, String name) {
    int end = offset+name.length();
    String actual = (end > _source.length()) ?
      ("<overflow:" + end + ">") : _source.substring(offset, end);
    if (!actual.equals(name)) {
      System.out.println("!!invalid name in " + whence +
                         " [name=" + name + ", offset=" + offset + ", actual=" + actual + "]");
      System.out.println(_source);
    }
  }

  private PrintWriter emit (String key, Object... keyVals) {
    PrintWriter out = _out;
    for (int ii = 0, ll = _indent; ii < ll; ii++) out.print(" ");
    out.print(key);
    if (keyVals.length > 0) {
      out.print(" {");
      for (int ii = 0; ii < keyVals.length; ii += 2) {
        if (ii > 0) out.print(", ");
        out.print(keyVals[ii]);
        out.print("=");
        out.print(keyVals[ii+1]);
      }
      out.print("}");
    }
    out.println();
    return out;
  }

  private final PrintWriter _out;
  private final String _source;
  private int _indent = 0;
}
