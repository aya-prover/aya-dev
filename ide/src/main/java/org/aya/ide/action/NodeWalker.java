// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ide.action;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import kala.collection.immutable.ImmutableSeq;
import kala.control.Either;
import kala.text.StringSlice;
import kotlin.sequences.Sequence;
import kotlin.sequences.SequencesKt;
import org.aya.ide.util.XY;
import org.aya.intellij.GenericNode;
import org.aya.util.Panic;
import org.aya.util.position.SourceFile;
import org.aya.util.position.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NodeWalker {
  public record Result(@NotNull GenericNode<?> node, int offsetInNode) { }

  private NodeWalker() { }

  /// Find the **node** under [#location], note that it could be empty (such as [com.intellij.psi.PsiWhiteSpace])
  public static @NotNull Result run(
    @NotNull SourceFile file,
    @NotNull GenericNode<?> node,
    @NotNull XY location,
    @NotNull TokenSet leafTypes
  ) {
    while (true) {
      if (leafTypes.contains(node.elementType())) break;

      var children = node.childrenView().toSeq();
      if (children.isEmpty()) break;

      var idx = binarySearch(children, file, location);
      // normally [idx] won't be negative, as the [TextRange]s of children are continuous.
      if (idx < 0) Panic.unreachable();   // TODO: what should i do?
      node = children.get(idx);
    }

    // [location] must inside [node], therefore `location.x() - 1` must less than `lineOffsets().size()`
    var lineOffsetInFile = file.lineOffsets().get(location.x() - 1);
    // lineOffsetInFile is the index of the first character of line `location.x()`, and `location.y()` is count from 0, so we have:
    var index = lineOffsetInFile + location.y();
    var offsetInNode = index - node.range().getStartOffset();

    return new Result(node, offsetInNode);
  }

  private static int binarySearch(@NotNull ImmutableSeq<? extends GenericNode<?>> nodes, @NotNull SourceFile file, @NotNull XY location) {
    int low = 0;
    int high = nodes.size() - 1;

    while (low <= high) {
      int mid = (low + high) >>> 1;
      var node = nodes.get(mid);
      var range = SourcePos.of(node.range(), file, false);
      var cmp = -range.compareVisually(location.x(), location.y());

      if (cmp == 0) return mid;
      if (cmp < 0) low = mid + 1;
      else high = mid - 1;
    }

    return -(low + 1);
  }

  public static @NotNull GenericNode<?> rightMost(@NotNull GenericNode<?> node) {
    if (node.childrenView().isEmpty()) return node;
    return rightMost(node.lastChild());
  }

  /// Place the cursor [#node] to the right level and right token.
  /// This function move the cursor to the left token if:
  /// * [#node] is not [TokenType#WHITE_SPACE] and the cursor is at the beginning of [#node]
  /// * [#node] is [TokenType#WHITE_SPACE]
  ///
  /// @param node cannot be [EmptyNode]
  /// @apiNote It is possible that [#node] is returned while it is a [com.intellij.psi.PsiWhiteSpace]
  public static @NotNull GenericNode<?> refocus(@NotNull GenericNode<?> node, int offsetInNode) {
    var parent = node.parent();
    assert parent != null;

    if (node.elementType() != TokenType.WHITE_SPACE && offsetInNode == 0) {
      // TODO: add condition: only refocus to the left if the cursor is at the beginning of [node].
      // We always refocus on the left most token, as the cursor is at the left side of [node].
      var prevToken = prevToken(node);
      assert prevToken != null;
      // in case of consecutive tokens
      if (prevToken.elementType() != TokenType.WHITE_SPACE) return new EmptyNode(prevToken);
      node = prevToken;
    }

    if (node.elementType() == TokenType.WHITE_SPACE) {
      var prevToken = node;
      while (prevToken.elementType().equals(TokenType.WHITE_SPACE)) {
        var token = prevToken(prevToken);
        if (token == null) return prevToken;
        prevToken = token;
      }

      return new EmptyNode(prevToken);
    }

    return node;
  }

  /// @return the previous token of [#node], null if [#node] is the first token
  public static @Nullable GenericNode<?> prevToken(@NotNull GenericNode<?> node) {
    var parent = node.parent();
    while (parent != null && node.equals(parent.firstChild())) {
      var tmp = parent;
      parent = tmp.parent();
      node = tmp;
    }

    if (parent == null) return null;

    // now [parent != null && node != parent.firstChild()]
    // also, the original node is the first token of [node]

    var siblings = parent.childrenView().toSeq();
    var idx = siblings.indexOf(node);
    // since [node != parent.firstChild()]
    var prevSibling = siblings.get(idx - 1);
    return rightMost(prevSibling);
  }

  /// An [EmptyNode] that lives right after [#host].
  /// [EmptyNode] is a pseudo-sibling of [#host].
  public final static class EmptyNode extends UserDataHolderBase implements GenericNode<EmptyNode> {
    private final @NotNull GenericNode<?> host;
    private final @NotNull TextRange range;

    public EmptyNode(@NotNull GenericNode<?> host) {
      var offset = host.range().getStartOffset() + 1;

      this.host = host;
      this.range = new TextRange(offset, offset);
    }

    @Override
    public @NotNull IElementType elementType() {
      return TokenType.DUMMY_HOLDER;
    }

    @Override
    public @NotNull StringSlice tokenText() {
      return StringSlice.empty();
    }

    @Override
    public @NotNull TextRange range() {
      return this.range;
    }

    @Override
    public @Nullable GenericNode<?> parent() {
      return host.parent();
    }

    public @NotNull GenericNode<?> host() {
      return host;
    }

    @Override
    public @NotNull Sequence<EmptyNode> childrenSequence() {
      return SequencesKt.emptySequence();
    }

    @Override public String toString() {
      final StringBuilder sb = new StringBuilder("EmptyNode{");
      sb.append("host=").append(host);
      sb.append(", range=").append(range);
      sb.append('}');
      return sb.toString();
    }
  }
}
