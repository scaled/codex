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
   * Returns all defs nested immediately inside the def with id {@code defId}. This does not return
   * defs nested two or more levels deep.
   * @throws NoSuchElementException if no def exists with that id.
   */
  public abstract Iterable<Def> memberDefs (int defId);

  /**
   * Returns all defs nested immediately inside the def with id {@code defId}. This does not return
   * uses nested inside defs which are themselves nested in {@code defId}, only uses that occur
   * directly in the body of {@code defId}.
   * @throws NoSuchElementException if no def exists with that id.
   */
  public abstract Iterable<Def> uses (int defId);

  /** Returns the signature for the def with id {@code defId}. */
  public abstract Sig sig (int defId);

  /** Returns the documentation for the def with id {@code defId}. */
  public abstract Doc doc (int defId);

  /**
   * Returns the source file in which the def with id {@code defId} originates. This will be an
   * absolute path to a source file, or the absolute path to an archive file followed by {@code !}
   * followed by the path to the source file inside the archive. Examples: {@code /foo/bar/Baz.java}
   * {@code /foo/bar/baz.jar!bing/bang/Boom.java}.
   */
  public abstract String source (int defId);

  protected ProjectStore (int projectId) {
    this.projectId = projectId;
  }
}
