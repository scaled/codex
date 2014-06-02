//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.store;

import codex.model.Ref;
import com.carrotsearch.hppc.IntObjectMap;
import com.carrotsearch.hppc.IntObjectOpenHashMap;
import com.carrotsearch.hppc.IntOpenHashSet;
import com.carrotsearch.hppc.IntSet;
import com.carrotsearch.hppc.cursors.IntCursor;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Maintains a mapping from {@link Ref.Global} to an integer identifier. The refs are stored in a
 * trie structure to minimize memory footprint and provide for efficient lookup. This class is
 * thread-safe by virtue of brute-force synchronization.
 */
public class RefTree {

  /**
   * Returns the id for {@code ref} if an assignment exists, 0 otherwise.
   */
  public synchronized int get (Ref.Global ref) {
    Node node = getNode(ref);
    return (node == null) ? 0 : node.id;
  }

  /**
   * Returns the global ref for {@code defId}.
   * @throws NoSuchElementException if no def exists with that id.
   */
  public synchronized Ref.Global get (int defId) {
    Node node = _byId.get(defId);
    if (node == null) throw new NoSuchElementException("No def with id " + defId);
    if (node.ref == null) throw new IllegalStateException("Def node lacks ref " + defId);
    return node.ref;
  }

  /**
   * Resolves the id for {@code ref}. If an assignment exists, it will be reused, otherwise a new
   * assignment will be created.
   * @param assignId the id to assign to the def if it's newly created.
   */
  public synchronized int resolve (Ref.Global ref, int assignId) {
    return resolveNode(ref).resolveId(assignId);
  }

  /**
   * Inserts the supplied {@code ref -> id} assignment.
   * @throws IllegalArgumentException if an assignment already exists for {@code ref} or {@code id}
   * is already assigned to another ref.
   */
  public synchronized void insert (Ref.Global ref, int id) {
    Node have = _byId.get(id);
    if (have != null) throw new IllegalArgumentException(
      "Id already in use: " + ref + " -> " + id + " (have " + have.ref + ")");
    Node node = resolveNode(ref);
    if (node.id != 0) throw new IllegalArgumentException(
      "Ref already in use: " + ref + "-> " + id + " (have + " + node.id + ")");
    node.id = id;
    _byId.put(id, node);
  }

  /** Returns a copy of all ids in this map. */
  public synchronized IntSet copyIds () {
    return new IntOpenHashSet(_byId.keys());
  }

  /** Removes mappings for all refs with ids in {@code ids}. */
  public synchronized void remove (IntSet ids) {
    for (IntCursor ic : ids) {
      Node node = _byId.get(ic.value);
      if (node != null) node.id = 0;
    }
    _byId.removeAll(ids);
    // we leave the nodes in the tree for now; if this turns out to be a big memory issue, we can go
    // through the extra effort to purge removed tree nodes which have no children
  }

  private Node getNode (Ref.Global ref) {
    if (ref == Ref.Global.ROOT) return _root;
    else {
      Node node = getNode(ref.parent);
      return (node == null) ? null : node.get(ref);
    }
  }

  private Node resolveNode (Ref.Global ref) {
    if (ref == Ref.Global.ROOT) return _root;
    else return resolveNode(ref.parent).resolve(ref);
  }

  private class Node {
    public final Ref.Global ref;
    public int id;
    public Map<String,Node> children;

    public Node (Ref.Global ref) {
      this.ref = ref;
    }

    public Node resolve (Ref.Global ref) {
      Node child;
      if (children != null) child = children.get(ref.id);
      else {
        children = new HashMap<>();
        child = null;
      }
      if (child == null) {
        children.put(ref.id, child = new Node(ref));
      }
      return child;
    }

    public Node get (Ref.Global ref) {
      return (children == null) ? null : children.get(ref.id);
    }

    public int resolveId (int assignId) {
      int id = this.id;
      if (id != 0) return id;
      this.id = assignId;
      _byId.put(assignId, this);
      return assignId;
    }
  }

  private Node _root = new Node(null);
  private IntObjectMap<Node> _byId = new IntObjectOpenHashMap<>();
}
