//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.extract;

import codex.model.*;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import java.io.BufferedReader;
import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StreamTokenizer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Map;
import java.util.function.Predicate;

public class TokenExtractor extends AbstractExtractor {

  // used to keep track of file offset since StreamTokenizer doesn't do that for us; note this
  // relies on the fact that StreamTokenizer only ever calls Reader.read()
  private class CountingReader extends FilterReader {
    public int offset = 0;
    public StringBuilder curline = new StringBuilder();
    public boolean atEOL;
    public CountingReader (Reader reader) {
      super(reader);
    }
    @Override public int read () throws IOException {
      offset += 1;
      int c = super.read();
      if (atEOL) curline.setLength(0);
      atEOL = (c == '\n');
      if (!atEOL) curline.append((char)c);
      return c;
    }
  }

  @Override
  protected void process (Source source, Reader reader, Writer writer) throws IOException {
    String lang = source.fileExt().intern();
    Map<String,Kind> kinds = kindsFor(lang);
    String prevtok = "";
    String curdef = "";
    Ref.Global curid = openUnit(source, writer);
    Deque<String> blocks = new ArrayDeque<>();

    CountingReader counter = new CountingReader(new BufferedReader(reader));
    StreamTokenizer tok = toker(counter);
    // treat # as a line comment starter in C# so that we ignore compiler directives
    if (lang == "cs") tok.commentChar('#');

    while (tok.nextToken() != StreamTokenizer.TT_EOF) {
      String tokstr = tok.sval; // save this here; we may munge tok later

      if (tok.ttype == '{') {
        // note that we entered a block for our most recent def
        blocks.push(curdef);
        // and clear out curdef so that nested blocks for this def are ignored
        curdef = "";

      } else if (tok.ttype == '}') {
        // we may enter a block, enter a def which has no block, and then exit the enclosing block;
        // in that case we're also exiting that nested def, so emit it now
        if (curdef != "") {
          writer.closeDef();
          curid = curid.parent;
          curdef = "";
        }

        if (blocks.isEmpty()) {
          System.err.println("Mismatched close brace [file=" + source +
                             ", line=" + (tok.lineno()-1) + "]");
        } else {
          String popdef = blocks.pop();
          // if this block was associated with a def, we're exiting it
          if (popdef != "") {
            curid = curid.parent;
            writer.closeDef();
          }
        }

      } else if (tok.ttype == StreamTokenizer.TT_WORD) {
        if ("package".equals(prevtok) || "namespace".equals(prevtok)) {
          curdef = tok.sval;
          curid = curid.plus(curdef);
          int off = counter.offset-curdef.length()-1;
          writer.openDef(curid, curdef, Kind.MODULE, Flavor.NONE, true, Access.PUBLIC,
                         off, off, off);
          writer.emitSig(counter.curline.toString().trim());
          // if the next token is a semicolon (or if this is Scala or Kotlin and the next token is
          // not an open bracket), pretend the rest of the file is one big block
          int ntok = tok.nextToken();
          if (ntok == ';' || (ntok != '{' && (lang == "scala" || lang == "kt"))) {
            blocks.push(curdef);
            curdef = "";
          }
          tok.pushBack();

        } else {
          Kind kind = kinds.get(prevtok);
          if (kind != null) {
            // if our previous def had no block associated with it, we're exiting it now
            if (curdef != "") {
              curid = curid.parent;
              writer.closeDef();
            }
            curdef = tok.sval;
            curid = curid.plus(curdef);
            int off = counter.offset-curdef.length()-1;
            writer.openDef(curid, curdef, kind, Flavor.NONE, true, Access.PUBLIC, off, off, off);
            writer.emitSig(counter.curline.toString().trim());
          }
        }
        prevtok = tokstr;
      }
    }

    // if our last def had no block associated with it, we're exiting it now
    if (curdef != "") writer.closeDef();

    closeUnit(source, writer);
  }

  protected Ref.Global openUnit (Source source, Writer writer) {
    writer.openUnit(source);
    return Ref.Global.ROOT;
  }

  protected void closeUnit (Source source, Writer writer) {
    writer.closeUnit();
  }

  /** Tokens that will appear prior to an element declaration, by language. */
  protected static Map<String,Kind> kindsFor (String suff) {
    switch (suff) {
      case "scala": return ImmutableMap.of(
        "class", Kind.TYPE, "object", Kind.MODULE, "trait", Kind.TYPE, "def", Kind.FUNC);
      case "as": return ImmutableMap.of(
        "class", Kind.TYPE, "interface", Kind.TYPE);
      case "cs": return ImmutableMap.of(
        "class", Kind.TYPE, "interface", Kind.TYPE, "enum", Kind.TYPE, "struct", Kind.TYPE);
      case "kt": return ImmutableMap.of(
        "class", Kind.TYPE, "object", Kind.MODULE, "interface", Kind.TYPE, "fun", Kind.FUNC);
      case "js": return ImmutableMap.of(
        "class", Kind.TYPE, "interface", Kind.TYPE, "type", Kind.TYPE, "function", Kind.FUNC);
      default: throw new IllegalArgumentException("Unsupported language: " + suff);
    }
  }

  /** Creates a {@link StreamTokenizer} configured for parsing C-like source code. */
  protected static StreamTokenizer toker (Reader reader) {
    StreamTokenizer tok = new StreamTokenizer(reader);
    tok.ordinaryChar('/'); // why do they call this a comment char by default?
    tok.wordChars('_', '_'); // allow _ in class names
    tok.slashSlashComments(true);
    tok.slashStarComments(true);
    return tok;
  }
}
