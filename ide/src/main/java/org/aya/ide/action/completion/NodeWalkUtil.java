// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ide.action.completion;

import com.intellij.psi.tree.TokenSet;
import kala.collection.SeqView;
import org.aya.intellij.GenericNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.aya.parser.AyaPsiElementTypes.DO_BINDING;
import static org.aya.parser.AyaPsiElementTypes.LET_BIND;

public final class NodeWalkUtil {
  public static final @NotNull TokenSet RIGHT_OPEN_BINDING_INTRODUCER = TokenSet.create(
    DO_BINDING,
    LET_BIND
  );

  private NodeWalkUtil() { }

  /// Collect all siblings before [#node]
  public static @NotNull SeqView<GenericNode<?>> backward(@NotNull GenericNode<?> node) {
    if (node instanceof NodeWalker.EmptyNode enode) {
      return backward(enode.host()).appended(enode.host());
    }

    var parent = node.parent();
    if (parent == null) return SeqView.empty();

    return SeqView.narrow(parent.childrenView().takeWhile(n -> !n.equals(node)));
  }

  /// Return the parent node of [#node],
  /// this function will return [NodeWalker.EmptyNode] if [#node] is [NodeWalker.EmptyNode]
  /// and the host of [#node] is the last node of its parent (in other word, [#node] is also at the end of its parent).
  public static @Nullable GenericNode<?> refocusParent(@NotNull GenericNode<?> node) {
    var parent = node.parent();
    if (parent == null) return null;
    if (!(node instanceof NodeWalker.EmptyNode enode)) return parent;
    if (parent.lastChild().equals(enode.host())
      && !RIGHT_OPEN_BINDING_INTRODUCER.contains(parent.elementType()))
      return new NodeWalker.EmptyNode(parent);
    return parent;
  }
}
