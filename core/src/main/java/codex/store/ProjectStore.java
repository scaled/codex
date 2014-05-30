//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.store;

import codex.model.*;

/**
 * Contains metadata for a single project.
 */
public abstract class ProjectStore {

  /** Uniquely identifies this project among all projects known to Codex. */
  public final int projectId;

  /**
   * Returns the def with id {@code defId}.
   * @throws NoSuchElementException if no def exists with that id.
   */
  public abstract Def def (int defId);

  /**
   * Returns all defs nested immediately inside {@code defId}. This does not return defs nested two
   * or more levels deep.
   * @throws NoSuchElementException if no def exists with that id.
   */
  public abstract Iterable<Def> memberDefs (int defId);

  /**
   * Returns all uses nested immediately inside {@code defId}. This does not return uses nested
   * inside defs which are themselves nested in {@code defId}, only uses that occur directly in the
   * body of {@code defId}.
   * @throws NoSuchElementException if no def exists with that id.
   */
  public abstract Iterable<Use> uses (int defId);

  /** Returns the signature for {@code defId}. */
  public abstract Sig sig (int defId);

  /** Returns the documentation for {@code defId}. */
  public abstract Doc doc (int defId);

  /**
   * Returns the source from which {@code defId} originates.
   */
  public abstract Source source (int defId);

  protected ProjectStore (int projectId) {
    this.projectId = projectId;
  }
}
