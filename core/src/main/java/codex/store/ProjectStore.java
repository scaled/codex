//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.store;

import codex.model.*;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Contains metadata for a single project.
 */
public abstract class ProjectStore implements AutoCloseable {

  /** Uniquely identifies this project among all projects known to Codex. */
  public final int projectId;

  /**
   * Returns the list of all top-level defs in this project.
   */
  public abstract Iterable<Def> topLevelDefs ();

  /**
   * Returns true if {@code source} is indexed by this project, false otherwise.
   */
  public abstract boolean isIndexed (Source source);

  /**
   * Returns the list of all defs in the specified source file.
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
  public abstract Def def (int defId);

  /**
   * Returns all defs nested immediately inside {@code defId}. This does not return defs nested two
   * or more levels deep.
   * @throws NoSuchElementException if no def exists with that id.
   */
  public abstract List<Def> memberDefs (int defId);

  /**
   * Returns all uses nested immediately inside {@code defId}. This does not return uses nested
   * inside defs which are themselves nested in {@code defId}, only uses that occur directly in the
   * body of {@code defId}.
   * @throws NoSuchElementException if no def exists with that id.
   */
  public abstract List<Use> uses (int defId);

  /** Returns the signature for {@code defId}. */
  public abstract Optional<Sig> sig (int defId);

  /** Returns the documentation for {@code defId}. */
  public abstract Optional<Doc> doc (int defId);

  /**
   * Returns the source from which {@code defId} originates.
   */
  public abstract Source source (int defId);

  protected ProjectStore (int projectId) {
    this.projectId = projectId;
  }
}
