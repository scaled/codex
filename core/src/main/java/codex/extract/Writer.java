//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.extract;

import codex.model.*;

/**
 * Provides an API via which compilers or other code analyzers can emit Codex model data while
 * traversing their own internal ASTs. Calls should occur in the following order:
 *
 * <pre>{@code
 * [openSession
 *   [openUnit
 *     [openDef
 *       emitSig?
 *         emitSigDef*
 *         emitSigUse*
 *       emitDoc?
 *         emitDocUse*
 *       emitRelation*
 *       emitUse*
 *       [openDef..closeDef]*
 *     closeDef]*
 *   closeUnit]*
 * closeSession]
 * }</pre>
 *
 * A * indicates that a method can be called zero or more times. A ? indicates that a method can be
 * called zero or one times. If a method is not called, the calls nested "inside" it must not be
 * called (i.e. emitSigDef without a preceding call to emitSig is invalid).
 */
public abstract class Writer {

  public abstract void openSession ();
  public abstract void openUnit (Source source);

  public abstract void openDef (Ref.Global id, String name, Kind kind, Flavor flavor,
                                boolean exported, Access access,
                                int offset, int bodyStart, int bodyEnd);

  public abstract void emitSig (String text);
  public abstract void emitSigDef (Ref.Global id, String name, Kind kind, int offset);
  public abstract void emitSigUse (Ref.Global target, String name, Kind kind, int offset);

  public abstract void emitDoc (int offset, int length);
  public abstract void emitDocUse (Ref.Global target, String name, Kind kind, int offset);

  public abstract void emitRelation (Relation relation, Ref.Global target);
  public abstract void emitUse (Ref.Global target, String name, Kind kind, int offset);

  public abstract void closeDef ();
  public abstract void closeUnit ();
  public abstract void closeSession ();
}
