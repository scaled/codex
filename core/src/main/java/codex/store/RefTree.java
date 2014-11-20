//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.store;

import codex.model.Ref;
import java.util.NoSuchElementException;

/**
 * Maintains a mapping from {@link Ref.Global} to an integer identifier.
 */
public abstract class RefTree {

  /**
   * Returns the id for {@code ref} if an assignment exists, null otherwise.
   */
  public abstract Long get (Ref.Global ref);

  /**
   * Returns the global ref for {@code defId}.
   * @throws NoSuchElementException if no def exists with that id.
   */
  public abstract Ref.Global get (Long defId);

  /**
   * Resolves the id for {@code ref}. If an assignment exists, it will be reused, otherwise a new
   * assignment will be created.
   * @param assignId the id to assign to the def if it's newly created.
   */
  public abstract Long resolve (Ref.Global ref, Long assignId);

  /** Removes mappings for all refs with ids in {@code ids}. */
  public abstract void remove (Iterable<Long> ids);

  /** Completely clears the contents of this tree. */
  public abstract void clear ();
}
