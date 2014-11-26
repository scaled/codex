//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.model;

import codex.store.ProjectStore;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Utility methods for doing OO things with the Codex model.
 */
public class OO {

  /**
   * Given a {@link Kind.TYPE} def which represents a class, this method resolves all {@link
   * Kind.FUNC} defs nested directly in the class, as well as all defs included in the transitive
   * closure of the defs related to class via the {@link Relation.INHERITS} and {@link SUPERTYPE}
   * relations. {@link Relation.OVERRIDES} is used to filter out methods from supertypes which are
   * overridden in a subtype.
   */
  public static List<Def> resolveMethods (Iterable<ProjectStore> stores, Def clazz) {
    Set<Ref.Global> skips = new HashSet<>(), seen = new HashSet<>();
    seen.add(clazz.globalRef());
    List<Def> mems = new ArrayList<>(), remain = new ArrayList<>();
    remain.add(clazz);

    while (!remain.isEmpty()) {
      Def typ = remain.remove(0);
      for (Def mem : typ.members()) {
        if (mem.kind != Kind.FUNC) continue;
        for (Ref oref : mem.relations(Relation.OVERRIDES)) skips.add(toGlobalRef(oref));
        if (!skips.contains(mem.globalRef())) mems.add(mem);
      }

      // we're a bit cheeky assuming relations returns a mutable set, but oh well
      Set<Ref> supers = typ.relations(Relation.INHERITS);
      supers.addAll(typ.relations(Relation.SUPERTYPE));
      for (Ref styp : supers) {
        Ref.Global tref = toGlobalRef(styp);
        if (!seen.contains(tref)) {
          seen.add(tref);
          Ref.resolve(stores, styp).ifPresent(remain::add);
        }
      }
    }
    return mems;
  }

  protected static Ref.Global toGlobalRef (Ref ref) {
    if (ref instanceof Ref.Global) return (Ref.Global)ref;
    else {
      Ref.Local loc = (Ref.Local)ref;
      return loc.project.def(loc.defId).globalRef();
    }
  }
}
