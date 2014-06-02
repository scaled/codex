//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.model;

import java.util.List;

/**
 * Contains information on documentation for a def.
 */
public class Doc {

  /** The character offset into the def's source file at which the documentation starts. */
  public final int offset;

  /** The length in characters of the def's documentation. */
  public final int length;

  /** Zero or more uses that occur in the documentation. The offset of the uses will be relative to
    * the documentation offset, not absolute in the file. */
  public final List<Use> uses;

  public Doc (int offset, int length, List<Use> uses) {
    this.offset = offset;
    this.length = length;
    this.uses = uses;
  }

  @Override public String toString () {
    return String.format("[%d, %d)", offset, length);
  }
}
