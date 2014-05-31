//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.extract;

import codex.model.*;
import codex.store.ProjectStore;
import com.google.common.collect.Lists;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;

/**
 * A writer tailored to storing metadata in a {@link ProjectStore}.
 */
public abstract class StoreWriter extends Writer {

  @Override public void openUnit (Source source) {
    _curUnitId = assignUnitId(source);
  }

  @Override public void openDef (Ref.Global id, String name, Kind kind, Flavor flavor,
                                 boolean exported, int offset, int bodyOffset, int bodyEnd) {
    int outerId = _defIdStack.peek();
    int defId = resolveDefId(id);
    _defIdStack.push(defId);
    // TODO: add flavor, bodyOffset, bodyEnd to Def? track them separately?
    storeDef(new Def(defId, outerId, kind, exported, name, offset));
  }

  @Override public void emitRelation (Relation relation, Ref.Global target) {
    // TODO
  }

  @Override public void emitSig (String text) {
    _curSig = new Sig(text, Lists.newArrayList(), Lists.newArrayList());
  }
  @Override public void emitSigDef (Ref.Global id, String name, Kind kind, int offset) {
    _curSig.defs.add(new Def(resolveDefId(id), 0, kind, false, name, offset));
  }
  @Override public void emitSigUse (Ref.Global target, String name, Kind kind, int offset) {
    _curSig.uses.add(new Use(target, kind, offset, name.length()));
  }

  @Override public void emitDoc (int offset, int length) {
    _curDoc = new Doc(offset, length, Lists.newArrayList());
  }
  @Override public void emitDocUse (Ref.Global target, String name, Kind kind, int offset) {
    _curDoc.uses.add(new Use(target, kind, offset, name.length()));
  }

  @Override public void emitUse (Ref.Global target, String name, Kind kind, int offset) {
    storeUse(_defIdStack.peek(), new Use(target, kind, offset, name.length()));
  }

  @Override public void commitDef () {
    int defId = _defIdStack.peek();
    if (_curSig != null) {
      storeSig(defId, _curSig);
      _curSig = null;
    }
    if (_curDoc != null) {
      storeDoc(defId, _curDoc);
      _curDoc = null;
    }
  }

  @Override public void closeDef () {
    _defIdStack.pop();
  }
  @Override public void closeUnit () {
    // TODO: track def ids in this unit, emit all at once
    _curUnitId = 0;
  }

  protected abstract int assignUnitId (Source source);
  protected abstract int resolveDefId (Ref.Global id);
  protected abstract void storeDef (Def def);
  protected abstract void storeUse (int defId, Use use);
  protected abstract void storeSig (int defId, Sig sig);
  protected abstract void storeDoc (int defId, Doc doc);

  protected int _curUnitId;
  protected Deque<Integer> _defIdStack = new ArrayDeque<>(Collections.singleton(0));
  protected Sig _curSig;
  protected Doc _curDoc;
}
