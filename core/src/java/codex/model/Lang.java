//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.model;

/**
 * Models the operations and metadata specific to the programming languages supported by Codex. It's
 * a bit centralized to require that all languages be enumerated here, but it simplifies things
 * elsewhere, and we'll try to be timely about accepting pull requests adding new languages to this
 * enum. I prefer this approach to having some complex system of pluggable factories and blah blah.
 */
public enum Lang {

  /** http://www.oracle.com/us/technologies/java/overview/index.html */
  JAVA {
    @Override public String pathPrefix (Kind kind) {
      switch (kind) {
        default:
        case MODULE:
        case TYPE: return ".";
        case FUNC: return "::";
        case VALUE: return " ";
      }
    }
  },

  /** http://www.scala-lang.org/ */
  SCALA,

  /** http://www.w3.org/XML/ */
  XML,

  /** Used when we can't identify any other language. */
  UNKNOWN;

  /** Returns the language for the supplied file extension. */
  public static Lang forExt (String suff) {
    switch (suff) {
      case "java":  return JAVA;

      case "sbt":
      case "scala": return SCALA;

      case "xml":   return XML;

      default:      return UNKNOWN;
    }
  }

  /**
   * Returns the string used to join a qualifier to an element of kind {@code kind}. This is used in
   * constructing the fully qualified name for a global ref.
   */
  public String pathPrefix (Kind kind) {
    return ".";
  }
}
