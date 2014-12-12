//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.model;

import codex.store.ProjectStore;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Utility methods for doing OO things with the Codex model.
 */
public class OO {

  /**
   * Computes the linearization the supertypes of {@code clazz}. {@code clazz} will be included as
   * well.
   */
  public static List<Def> linearizeSupers (Iterable<ProjectStore> stores, Def clazz) {
    Set<Ref.Global> seen = new HashSet<>();
    seen.add(clazz.globalRef());
    List<Def> types = new ArrayList<>();
    types.add(clazz);

    // TODO: have the extractor tag defs with a flag that indicates that they're a type root, then
    // we don't have to do this hacky language detection and blah blah
    Lang lang = Lang.forExt(clazz.source().fileExt());

    // keep adding new types as we see them; when we get to the end, we've added no new supertypes
    for (int ii = 0; ii < types.size(); ii++) {
      Def type = types.get(ii);
      // we're a bit cheeky assuming relations returns a mutable set, but oh well
      Set<Ref> supers = type.relations(Relation.INHERITS);
      supers.addAll(type.relations(Relation.SUPERTYPE));
      for (Ref styp : supers) {
        Ref.Global tref = toGlobalRef(styp);
        if (!seen.contains(tref)) {
          seen.add(tref);
          Ref.resolve(stores, styp).ifPresent(sdef -> {
            if (!lang.isRoot(sdef)) types.add(sdef);
          });
        }
      }
    }
    return types;
  }

  /**
   * Resolves all {@link Kind.FUNC} defs defined by the supplied {@code types} (which usually came
   * from a call to {@link #linearizeSupers}). {@link Relation.OVERRIDES} is used to filter out
   * methods from supertypes which are overridden in a subtype.
   *
   * @param filter a filter applied to all member defs prior to inclusion. This can be used to
   * filter out, for example, static defs or private defs, or whatnot.
   */
  public static List<Def> resolveMethods (List<Def> types, Predicate<Def> filter) {
    Set<Ref.Global> skips = new HashSet<>();
    List<Def> mems = new ArrayList<>();
    for (Def type : types) {
      for (Def mem : type.members()) {
        if (mem.kind != Kind.FUNC || !filter.test(mem)) continue;
        for (Ref oref : mem.relations(Relation.OVERRIDES)) skips.add(toGlobalRef(oref));
        if (!skips.contains(mem.globalRef())) mems.add(mem);
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
