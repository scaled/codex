//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.model;

/**
 * Defines optional relations between definitions. These are used to model aspects of languages that
 * do not fit into the "hierarchy of nested defs" base model. Relations may be one to one, one to
 * many, many to one, or many to many, depending on the source language.
 */
public enum Relation {

  /** Indicates that the target def is a supertype of the source def. */
  SUPERTYPE,

  /** Indicates that the source def inherits from the target def. */
  INHERITS,

  /** Indicates that the source def overrides the target def. */
  OVERRIDES;
}
