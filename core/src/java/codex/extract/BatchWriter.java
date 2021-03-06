//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.extract;

import codex.model.*;
import codex.store.ProjectStore;
import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A writer that batches up all of the defs and uses for a single compilation unit and processes
 * them all at once.
 */
public abstract class BatchWriter extends Writer {

  public static class SigInfo {
    public final String text;
    public List<UseInfo> uses;

    public SigInfo (String text) {
      this.text = text;
    }
    public void addUse (UseInfo use) {
      if (uses == null) uses = new ArrayList<>();
      uses.add(use);
    }
  }

  public static class DocInfo {
    public final int offset, length;
    public List<UseInfo> uses;

    public DocInfo (int offset, int length) {
      this.offset = offset;
      this.length = length;
    }
    public void addUse (UseInfo use) {
      if (uses == null) uses = new ArrayList<>();
      uses.add(use);
    }
  }

  public static class UseInfo {
    public final Ref.Global ref;
    public final Kind refKind;
    public final int offset, length;

    public UseInfo (Ref.Global ref, Kind refKind, int offset, int length) {
      Preconditions.checkArgument(ref != null);
      this.ref = ref;
      this.refKind = refKind;
      this.offset = offset;
      this.length = length;
    }

    public Use resolve (ProjectStore store, Long refId) {
      Ref ref = (refId == null) ? this.ref : Ref.local(store, refId);
      return new Use(ref, refKind, offset, length);
    }
  }

  public static class RelInfo {
    public final Relation relation;
    public final Ref.Global target;

    public RelInfo (Relation relation, Ref.Global target) {
      this.relation = relation;
      this.target = target;
    }
  }

  public static class DefInfo {
    public final DefInfo outer;
    public final Ref.Global id;
    public final String name;
    public final Kind kind;
    public final Flavor flavor;
    public final boolean exported;
    public final Access access;
    public final int offset;
    public final int bodyStart;
    public final int bodyEnd;

    public SigInfo sig;
    public DocInfo doc;
    public List<DefInfo> defs;
    public List<UseInfo> uses;
    public List<RelInfo> relations;

    public Long defId; // this gets assigned in toDef()
    public HashSet<Long> memDefIds;

    public DefInfo (DefInfo outer, Ref.Global id, String name, Kind kind, Flavor flavor,
                    boolean exported, Access access, int offset, int bodyStart, int bodyEnd) {
      Preconditions.checkArgument(id != null);
      this.outer = outer;
      this.id = id;
      this.name = name;
      this.kind = kind;
      this.flavor = flavor;
      this.exported = exported;
      this.access = access;
      this.offset = offset;
      this.bodyStart = bodyStart;
      this.bodyEnd = bodyEnd;
    }

    public DefInfo addDef (DefInfo def) {
      if (defs == null) defs = new ArrayList<>();
      defs.add(def);
      return def;
    }

    public void addUse (UseInfo use) {
      if (uses == null) uses = new ArrayList<>();
      uses.add(use);
    }

    public void addRelation (Relation relation, Ref.Global target) {
      if (relations == null) relations = new ArrayList<>();
      relations.add(new RelInfo(relation, target));
    }

    public Def toDef (ProjectStore store, Long defId, Long outerId) {
      this.defId = defId;
      if (outer != null) outer.noteMemDef(defId);
      return new Def(store, defId, outerId, kind, flavor, exported, access, name,
                     offset, bodyStart, bodyEnd);
    }

    @Override public String toString () {
      return String.format("DefInfo(%s, %s, %s, %s, %s, %s, %s, %d, %d, %d)",
                           outer == null ? "null" : outer.name, id, name, kind, flavor, exported,
                           access, offset, bodyStart, bodyEnd);
    }

    private void noteMemDef (Long defId) {
      if (memDefIds == null) memDefIds = new HashSet<>();
      memDefIds.add(defId);
    }
  }

  @Override public void openUnit (Source source) {
    _curSource = source;
    _curDef = new DefInfo(null, Ref.Global.ROOT, null, null, null, false, null, 0, 0, 0);
  }

  @Override public void openDef (Ref.Global id, String name, Kind kind, Flavor flavor,
                                 boolean exported, Access access,
                                 int offset, int bodyStart, int bodyEnd) {
    checkTarget(id, "openDef");
    _curDef = _curDef.addDef(new DefInfo(_curDef, id, name, kind, flavor, exported, access,
                                         offset, bodyStart, bodyEnd));
  }

  @Override public void emitSig (String text) {
    _curDef.sig = new SigInfo(text);
  }
  @Override public void emitSigUse (Ref.Global target, Kind kind, int offset, int length) {
    checkTarget(target, "emitSigUse");
    _curDef.sig.addUse(new UseInfo(target, kind, offset, length));
  }

  @Override public void emitDoc (int offset, int length) {
    _curDef.doc = new DocInfo(offset, length);
  }
  @Override public void emitDocUse (Ref.Global target, Kind kind, int offset, int length) {
    checkTarget(target, "emitDocUse");
    _curDef.doc.addUse(new UseInfo(target, kind, offset, length));
  }

  @Override public void emitUse (Ref.Global target, Kind kind, int offset, int length) {
    checkTarget(target, "emitUse");
    _curDef.addUse(new UseInfo(target, kind, offset, length));
  }

  @Override public void emitRelation (Relation relation, Ref.Global target) {
    checkTarget(target, "emitRelation");
    _curDef.addRelation(relation, target);
  }

  @Override public void closeDef () {
    if (_curDef.outer == null) throw new IllegalStateException("Cannot close topDef.");
    _curDef = _curDef.outer;
  }
  @Override public void closeUnit () {
    storeUnit(_curSource, _curDef);
    _curSource = null;
    _curDef = null;
  }

  private void checkTarget (Ref.Global target, String from) {
    if (target == Ref.Global.ROOT) throw new IllegalArgumentException(
      "Cannot call " + from + " with ROOT name.");
  }

  protected abstract void storeUnit (Source source, DefInfo topDef);

  protected Source _curSource;
  protected DefInfo _curDef;
}
