//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.model;

/**
 * Enumerates different flavors of def. Defs are primarily organized into {@link Kind}, but for
 * display purposes, their kind is refined into a "flavor". This enumeration contains the union of
 * all flavors of all defs in all languages supported by Codex. A given language will likely only
 * use a subset of these flavors.
 *
 * Presently flavor is not used in searching or analysis, though it could be, in theory.
 */
public enum Flavor {

  // module flavors

  /** A package (Java, Scala). */
  PACKAGE,
  /** A namespace (C#, C++). */
  NAMESPACE,

  // type flavors

  /** A type parameter. */
  TYPE_PARAM,
  /** A singleton object (Scala). */
  OBJECT,
  /** An annotation type (Java, etc.). */
  ANNOTATION,
  /** An enumeration (Java, etc.). */
  ENUM,
  /** An interface (Java, etc.). */
  INTERFACE,
  /** An abstract class (Java, etc.). */
  ABSTRACT_CLASS,
  /** A plain old class (Java, etc.). */
  CLASS,
  /** An abstract singleton object (Scala). */
  ABSTRACT_OBJECT,

  // func flavors

  /** An object constructor (Java, etc.). */
  CONSTRUCTOR,
  /** An abstract (or interface) method (Java, etc.). */
  ABSTRACT_METHOD,
  /** A normal class method (Java, etc.). */
  METHOD,
  /** A static method (Java, etc.). */
  STATIC_METHOD,

  // term flavors

  /** An object field (Java, etc.). */
  FIELD,
  /** A class field (Java, etc.). */
  STATIC_FIELD,
  /** A function parameter (Java, etc.). */
  PARAM,
  /** A local variable (Java, etc.). */
  LOCAL,

  /** A flavorless def. */
  NONE;
}
