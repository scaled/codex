//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.model;

/**
 * Enumerates different access levels for a def. This is chiefly used for display purposes (and
 * grouping a def's members). This relates to {@link Def#exported} as {@link Def#flavor} relates to
 * {@link Def#kind}. This enumeration contains the union of all access levels used by all languages
 * supported by Codex. A given language will likely only use a subset of these access levels.
 */
public enum Access {

  // note: the order of these enum elements dictates the order in which members will be grouped when
  // displaying defs, so insert new access levels in a sensible position

  PUBLIC,            // most languages
  PROTECTED,         // most Java-like languages
  PACKAGE_PRIVATE,   // Java's default access, Scala's private[pkg] access
  PRIVATE,           // most Java-like languages, Scala's private[this] access
  LOCAL;             // for elements with no access level, i.e. local variables
}
