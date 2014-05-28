//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.extract;

import java.io.PrintWriter;
import codex.model.*;

/**
 * A {@code Writer} implementation that emits a simple text-based representation of the metadata.
 */
public class TextWriter extends Writer {

  public TextWriter (PrintWriter out) {
    _out = out;
  }

  public void openUnit (String path) {
    emit("unit", path);
    _indent += 1;
  }

  public void openDef (String id, String name, Kind kind, Flavor flavor, boolean exported,
                       int offset, int bodyStart, int bodyEnd) {
    emit("def", id);
    emit("name", name);
    emit("kind", kind);
    emit("flavor", flavor);
    emit("exported", exported);
    emit("offset", offset);
    emit("bodyStart", bodyStart);
    emit("bodyEnd", bodyEnd);
    _indent += 1;
  }

  public void emitRelation (Relation relation, String target) {
    emit("relation", relation, target);
  }

  public void emitSig (String text) {
    emit("sig", text);
  }
  public void emitSigDef (String id, String name, Kind kind, int offset) {
    emit("sigdef", "id", id);
    emit("sigdef", "name", name);
    emit("sigdef", "kind", kind);
    emit("sigdef", "offset", offset);
  }
  public void emitSigUse (String target, String name, Kind kind, int offset) {
    emit("siguse", "target", target);
    emit("siguse", "name", name);
    emit("siguse", "kind", kind);
    emit("siguse", "offset", offset);
  }

  public void emitDoc (int offset, int length) {
    emit("doc", "offset", offset);
    emit("doc", "length", length);
  }
  public void emitDocUse (String target, String name, Kind kind, int offset) {
    emit("docuse", "target", target);
    emit("docuse", "name", name);
    emit("docuse", "kind", kind);
    emit("docuse", "offset", offset);
  }

  public void emitUse (String target, String name, Kind kind, int offset) {
    emit("use", "target", target);
    emit("use", "name", name);
    emit("use", "kind", kind);
    emit("use", "offset", offset);
  }

  public void closeDef () {
    _indent -= 1;
  }

  public void closeUnit () {
    _indent -= 1;
  }

  private PrintWriter emit (String key) {
    PrintWriter out = _out;
    for (int ii = 0, ll = _indent; ii < ll; ii++) out.print(" ");
    out.print(key);
    return out;
  }

  private void emit (String key, Object value) {
    PrintWriter out = emit(key);
    out.print(" ");
    out.println(value);
  }

  private void emit (String key, Object value1, Object value2) {
    PrintWriter out = emit(key);
    out.print(" ");
    out.println(value1);
    out.print(" ");
    out.println(value2);
  }

  private final PrintWriter _out;
  private int _indent = 0;
}
