//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.extract;

import java.io.PrintWriter;
import codex.model.*;
import java.util.Collection;

/**
 * A {@code Writer} implementation that emits a simple text-based representation of the metadata.
 */
public class TextWriter extends Writer {

  public TextWriter (PrintWriter out) {
    _out = out;
  }

  @Override public void openUnit (String path) {
    emit("unit", path);
    _indent += 1;
  }

  @Override public void openDef (Collection<String> id, String name, Kind kind, Flavor flavor,
                                 boolean exported, int offset, int bodyStart, int bodyEnd) {
    emit("def", idToString(id));
    emit("name", name);
    emit("kind", kind);
    emit("flavor", flavor);
    emit("exported", exported);
    emit("offset", offset);
    emit("body", bodyStart, bodyEnd);
    _indent += 1;
  }

  @Override public void emitRelation (Relation relation, Collection<String> target) {
    emit("relation", relation, idToString(target));
  }

  @Override public void emitSig (String text) {
    emit("sig", text.replace('\n', '\t')); // TODO: undo this on in TextReader
  }
  @Override public void emitSigDef (Collection<String> id, String name, Kind kind, int offset) {
    emit("sigdef", "id", idToString(id));
    emit("sigdef", "name", name);
    emit("sigdef", "kind", kind);
    emit("sigdef", "offset", offset);
  }
  @Override public void emitSigUse (Collection<String> target, String name, Kind kind, int offset) {
    emit("siguse", "target", idToString(target));
    emit("siguse", "name", name);
    emit("siguse", "kind", kind);
    emit("siguse", "offset", offset);
  }

  @Override public void emitDoc (int offset, int length) {
    emit("doc", "offset", offset);
    emit("doc", "length", length);
  }
  @Override public void emitDocUse (Collection<String> target, String name, Kind kind, int offset) {
    emit("docuse", "target", idToString(target));
    emit("docuse", "name", name);
    emit("docuse", "kind", kind);
    emit("docuse", "offset", offset);
  }

  @Override public void emitUse (Collection<String> target, String name, Kind kind, int offset) {
    emit("use", "target", idToString(target));
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

  private String idToString (Collection<String> target) {
    StringBuilder buf = new StringBuilder();
    for (String part : target) {
      if (buf.length() > 0) buf.append('\t');
      buf.append(part);
    }
    return buf.toString();
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
    out.print(value1);
    out.print(" ");
    out.println(value2);
  }

  private final PrintWriter _out;
  private int _indent = 0;
}
