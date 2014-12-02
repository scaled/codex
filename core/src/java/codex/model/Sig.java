//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.model;

import java.util.List;

/**
 * Contains information on a def's signature.
 */
public class Sig {

  /** The text of the signature. */
  public String text;

  /** Any uses that occur in the signature. The offset of each use will be relative to the start of
    * {@link #text}. */
  public List<Use> uses;

  public Sig (String text, List<Use> uses) {
    this.text = text;
    this.uses = uses;
  }

  @Override public String toString () {
    return text;
  }
}
