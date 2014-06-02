//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.model;

import java.util.Optional;

/**
 * Groups a def with related information.
 */
public class DefInfo {

  /** Basic def information. */
  public final Def def;

  /** The source file in which this def occurs. */
  public final Source source;

  /** The signature of the def, and sig defs/uses, if available. */
  public final Optional<Sig> sig;

  /** The documentation for the def, if available. */
  public final Optional<Doc> doc;

  public DefInfo (Def def, Source source, Optional<Sig> sig, Optional<Doc> doc) {
    this.def = def;
    this.source = source;
    this.sig = sig;
    this.doc = doc;
  }

  @Override public String toString () {
    return def + " @ " + source;
  }
}
