// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DynamicForest {
  // The auxiliary tree, whose in-order traversal represents the depths in the original tree.
  static private final class Node {
    @Nullable Node parent, upper;
    @Nullable Node left, right;
    boolean reversed;

    @Nullable Node getChild(boolean isRight) {
      if (isRight) return right;
      return left;
    }

    void setChild(boolean isRight, @Nullable Node target) {
      if (isRight) {
        right = target;
      } else {
        left = target;
      }
    }

    boolean isRightChild() {
      return null != parent && this == parent.right;
    }

    Node() {
      parent = null;
      left = null;
      right = null;
      reversed = false;
    }

    void pushDown() {
      if (reversed) {
        var tmp = left;
        left = right;
        right = tmp;
        if (null != left) left.reversed = !left.reversed;
        if (null != right) right.reversed = !right.reversed;
        reversed = false;
      }
    }

    void rotate() {
      assert null != parent;

      // First, check the `reversed` flags for all touched nodes. Push down the flags if needed.
      if (null != parent.parent) parent.parent.pushDown();

      parent.pushDown();
      pushDown();

      // Secondly, during the process of going up, the lower node also need to hand over the pointer to upper-level
      // paths so that only the root for each auxiliary tree can have non-null upper pointers.
      var tmp = parent.upper;
      parent.upper = upper;
      upper = tmp;

      // Prepare to swap pointers
      var isRight = isRightChild();
      var parentBackup = parent;

      // Update parents
      if (null != parentBackup.parent) parentBackup.parent.setChild(parentBackup.isRightChild(), this);
      parent = parentBackup.parent;

      // Update children
      var targetChild = getChild(!isRight);
      parentBackup.setChild(isRight, targetChild);
      if (null != targetChild) targetChild.parent = parentBackup;

      // Update current node
      setChild(!isRight, parentBackup);
      parentBackup.parent = this;
    }

    // Keep rotating until current node reaches the root
    void splay() {
      while (null != parent) {
        // Check if we are only one step away from the root
        if (null == parent.parent) rotate();
        else {
          parent.parent.pushDown();
          parent.pushDown();
          if (isRightChild() == parent.isRightChild()) {
            parent.rotate();
            rotate();
          } else {
            rotate();
            rotate();
          }
        }
      }
    }

    // Separate current auxiliary tree into two smaller trees such that all nodes that are deeper than the current node
    // (those who come later during in-order traversal) are cut off from the auxiliary tree.
    // After the separation, current node is the root of its auxiliary tree.
    void separateDeeperNodes() {
      splay();
      pushDown();
      if (null != right) {
        right.parent = null;
        right.upper = this;
        right = null;
      }
    }

    // Merge current auxiliary tree with the upper-level one.
    // The merge process makes sure the current node is the "deepest" one in the merged auxiliary tree (by cutting off
    // irrelevant subtrees).
    // After the extension, current node is the root of the merged tree.
    // Return false if there is no upper level path.
    boolean extendToUpper() {
      splay();
      if (null == upper) return false;
      upper.separateDeeperNodes();
      upper.right = this;
      parent = upper;
      upper = null;
      return true;
    }

    // Extend the auxiliary tree all the way to root.
    // After the extension, current node is the root of its auxiliary tree.
    void extendToRoot() {
      separateDeeperNodes();
      var extensible = true;
      while (extensible) extensible = extendToUpper();
    }

    // Lift the node to the root of its tree (not the auxiliary tree).
    // To do so, we first extend the auxiliary tree to root, which represents the path from root to the current node.
    // To set the current node as root, we reverse the order of the auxiliary tree such that previous
    // root (who has the least depth) now has the deepest depth and the current node (who has the deepest depth) now has
    // the lowest depth.
    void liftToRoot() {
      extendToRoot();
      splay();
      reversed = !reversed;
    }

    // Find the min element in the sub auxiliary tree rooted at the given node.
    Node findMin() {
      var x = this;
      x.pushDown();
      while (null != x.left) {
        x = x.left;
        x.pushDown();
      }
      x.splay();
      return x;
    }

    Node findMax() {
      var x = this;
      x.pushDown();
      while (null != x.right) {
        x = x.right;
        x.pushDown();
      }
      x.splay();
      return x;
    }
  }

  public static final class Handle {
    private final @NotNull Node node;

    private Handle(@NotNull Node node) {
      this.node = node;
    }

    public boolean isConnected(@NotNull Handle v) {
      if (node == v.node) return true;
      node.liftToRoot();
      v.node.extendToRoot();
      v.node.splay();
      // If connected, `u` and `v` are in the same auxiliary tree.
      return v.node.findMin() == node;
    }

    public boolean isDirectlyConnected(@NotNull Handle v) {
      node.liftToRoot();
      v.node.extendToRoot();
      v.node.splay();
      v.node.pushDown();
      return v.node.left != null && v.node.left.findMax() == node;
    }

    // Connect two nodes. (user need to guarantee the graph is acyclic)
    public void connectUnchecked(@NotNull Handle v) {
      v.node.liftToRoot();
      v.node.upper = node;
    }

    // Disconnect two nodes. (user need to guarantee the nodes are previously connected)
    public void disconnectUnchecked(@NotNull Handle v) {
      node.liftToRoot();
      v.node.extendToRoot();
      v.node.splay();
      v.node.pushDown();
      // `v` is now the deepest node in the auxiliary tree -- it has no right child
      assert v.node.left != null;
      v.node.left.parent = null;
      v.node.left = null;
    }

    public void connect(@NotNull Handle v) {
      if (node != v.node && !isConnected(v)) connectUnchecked(v);
    }

    public void disconnect(@NotNull Handle v) {
      if (node != v.node && isDirectlyConnected(v)) disconnectUnchecked(v);
    }
  }

  public static @NotNull Handle create() {
    return new Handle(new Node());
  }
}
