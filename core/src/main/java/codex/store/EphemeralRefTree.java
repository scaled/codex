//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.store;

import codex.model.Ref;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Implements a {@link RefTree} via an in-memory store. The refs are stored in a trie structure to
 * minimize memory footprint and provide for efficient lookup. This class is thread-safe by virtue
 * of brute-force synchronization.
 */
public class EphemeralRefTree extends RefTree {

  /**
   * Returns the number of defs in this tree.
   */
  public synchronized int defCount () {
    return _byId.size();
  }

  /**
   * Returns the number of names in the ref tree.
   */
  public synchronized int nameCount () {
    return _root.size();
  }

  @Override public synchronized Long get (Ref.Global ref) {
    Node node = getNode(ref);
    return (node == null) ? null : node.id;
  }

  @Override public synchronized Ref.Global get (Long defId) {
    Node node = _byId.get(defId);
    if (node == null) throw new NoSuchElementException("No def with id " + defId);
    if (node.ref == null) throw new IllegalStateException("Def node lacks ref " + defId);
    return node.ref;
  }

  @Override public synchronized Long resolve (Ref.Global ref, Long assignId) {
    return resolveNode(ref).resolveId(assignId);
  }

  @Override public synchronized void remove (Iterable<Long> ids) {
    Set<Long> keySet = _byId.keySet();
    for (Long id : ids) {
      Node node = _byId.get(id);
      if (node != null) node.id = null;
      keySet.remove(id);
    }
    // we leave the nodes in the tree for now; if this turns out to be a big memory issue, we can go
    // through the extra effort to purge removed tree nodes which have no children
  }

  @Override public synchronized void clear () {
    _root.children.clear();
    _byId.clear();
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
    public Long id;
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

    public Long resolveId (Long assignId) {
      Long id = this.id;
      if (id != null) return id;
      this.id = assignId;
      _byId.put(assignId, this);
      return assignId;
    }

    public int size () {
      int size = (ref == null) ? 0 : 1;
      if (children != null) for (Node c : children.values()) size += c.size();
      return size;
    }
  }

  private final Node _root = new Node(null);
  private final Map<Long,Node> _byId = new HashMap<>();
}
