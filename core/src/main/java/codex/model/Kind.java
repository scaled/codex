//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.model;

/**
 * Denotes the different kinds of definitions that appear in source code.
 */
public enum Kind {

  /** A namespaced collection of types, functions and terms. Examples: a Java/C#/Scala package, a
   * Scala object, a C++ namespace. */
  MODULE,

  /** A named type, with type, function and term members. Examples: a Java/C#/Scala/C++ class or
   * interface, a C struct. */
  TYPE,

  /** A function, procedure or method. */
  FUNC,

  /** A named value. Examples: a Java/C#/C++ class field, a C struct member, a function parameter,
   * a local variable. */
  TERM;
}
