//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.store;

import codex.extract.Writer;
import codex.model.*;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Contains metadata for a single project. NOTE: the use of {@code Long} in this API does not mean
 * that null is an allowed argument. It is to avoid unnecessary boxing and unboxing of long ids.
 */
public abstract class ProjectStore implements AutoCloseable {

  /**
   * The user friendly name of this project.
   */
  public final String name;

  /**
   * Returns a writer that can be used to update this project.
   */
  public abstract Writer writer ();

  /**
   * Returns all top-level defs in this project.
   */
  public abstract Iterable<Def> topLevelDefs ();

  /**
   * Returns the timestamp at which {@code source} was last indexed by this project, 0L if it has
   * never been indexed by this project.
   */
  public abstract long lastIndexed (Source source);

  /**
   * Returns all defs in the specified source file.
   */
  public abstract Iterable<Def> sourceDefs (Source source);

  /**
   * Returns the def referred to by {@code ref}, if it is part of this project.
   */
  public abstract Optional<Def> def (Ref.Global ref);

  /**
   * Returns the def with id {@code defId}.
   * @throws NoSuchElementException if no def exists with that id.
   */
  public abstract Def def (Long defId);

  /**
   * Returns a global ref for {@code defId}.
   * @throws NoSuchElementException if no def exists with that id.
   */
  public abstract Ref.Global ref (Long defId);

  /**
   * Returns all defs nested immediately inside {@code defId}. This does not return defs nested two
   * or more levels deep.
   * @throws NoSuchElementException if no def exists with that id.
   */
  public abstract Iterable<Def> defsIn (Long defId);

  /**
   * Returns all uses nested immediately inside {@code defId}. This does not return uses nested
   * inside defs which are themselves nested in {@code defId}, only uses that occur directly in the
   * body of {@code defId}.
   * @throws NoSuchElementException if no def exists with that id.
   */
  public abstract List<Use> usesIn (Long defId);

  /**
   * Returns all uses of {@code def} which appear in any compilation units in this project. The def
   * need not originate from this project. The returned uses are returned as a mapping from source
   * file to the character offsets in said source file at which each use occurs.
   */
  public abstract Map<Source,int[]> usesOf (Def def);

  /** Returns the signature for {@code defId}. */
  public abstract Optional<Sig> sig (Long defId);

  /** Returns the documentation for {@code defId}. */
  public abstract Optional<Doc> doc (Long defId);

  /**
   * Returns the source from which {@code defId} originates.
   */
  public abstract Source source (Long defId);

  /**
   * Adds all defs to {@code into} that match {@code query}.
   * @param expdOnly if true, include only exported defs in the results; if false, include exported
   * and non-exported defs.
   */
  public abstract void find (Query query, boolean expOnly, List<Def> into);

  /**
   * Returns a debug representation of {@code id}.
   */
  public abstract String idToString (Long id);

  /**
   * Delivers all known defs and uses in {@code source} to {@code cons}. The order in which the defs
   * and uses is unspecified, other than that each def will be immediately followed by the uses
   * nested immediately inside that def.
   *
   * <p>This is generally used to build an in-memory index for a given source file for things like
   * code highlighting and name resolution.</p>
   *
   * @return true if elems were delivered, false if {@code source} was unknown to this store.
   */
  public boolean visit (Source source, Consumer<Element> cons) {
    if (lastIndexed(source) == 0L) return false;
    for (Def def : sourceDefs(source)) {
      cons.accept(def);
      for (Use use : usesIn(def.id)) cons.accept(use);
    }
    return true;
  }

  protected ProjectStore (String name) {
    this.name = name;
  }
}
