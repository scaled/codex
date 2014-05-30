//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.extract;

import codex.model.*;
import java.util.Collection;

/**
 * Provides an API via which compilers or other code analyzers can emit Codex model data while
 * traversing their own internal ASTs. Calls should occur in the following order:
 *
 * <pre>{@code
 * [openUnit
 *   [openDef
 *     emitRelation*
 *     emitSig?
 *       emitSigDef*
 *       emitSigUse*
 *     emitDoc?
 *       emitDocUse*
 *     emitUse*
 *   closeDef]*
 * closeUnit]*
 * }</pre>
 *
 * A * indicates that a method can be called zero or more times. A ? indicates that a method can be
 * called zero or one times. If a method is not called, the calls nested "inside" it must not be
 * called (i.e. emitSigDef without a preceding call to emitSig is invalid).
 */
public abstract class Writer {

  public abstract void openUnit (String path);
  public abstract void openDef (Id.Global id, String name, Kind kind, Flavor flavor,
                                boolean exported, int offset, int bodyOffset, int bodyEnd);

  public abstract void emitRelation (Relation relation, Id.Global target);

  public abstract void emitSig (String text);
  public abstract void emitSigDef (Id.Global id, String name, Kind kind, int offset);
  public abstract void emitSigUse (Id.Global target, String name, Kind kind, int offset);

  public abstract void emitDoc (int offset, int length);
  public abstract void emitDocUse (Id.Global target, String name, Kind kind, int offset);

  public abstract void emitUse (Id.Global target, String name, Kind kind, int offset);

  public abstract void closeDef ();
  public abstract void closeUnit ();
}
