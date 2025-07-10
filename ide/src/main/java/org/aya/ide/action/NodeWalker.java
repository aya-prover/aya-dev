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
import org.aya.parser.AyaParserDefinitionBase;
import org.aya.parser.AyaPsiElementTypes;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.concrete.Pattern;
import org.aya.util.Panic;
import org.aya.util.position.SourceFile;
import org.aya.util.position.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NodeWalker {
  private NodeWalker() { }

  /// Find the **node** under [#location], note that it could be empty (such as [com.intellij.psi.PsiWhiteSpace])
  public static @NotNull GenericNode<?> run(
    @NotNull SourceFile file,
    @NotNull GenericNode<?> node,
    @NotNull XY location,
    @NotNull TokenSet leafTypes
  ) {
    while (true) {
      if (leafTypes.contains(node.elementType())) break;

      var children = ImmutableSeq.<GenericNode<?>>narrow(node.childrenView().toSeq());
      if (children.isEmpty()) break;

      var idx = binarySearch(children, file, Either.left(location));
      // normally [idx] won't be negative, as the [TextRange]s of children are continuous.
      if (idx < 0) Panic.unreachable();   // TODO: what should i do?
      node = children.get(idx);
    }

    return node;
  }

  private static int binarySearch(@NotNull ImmutableSeq<GenericNode<?>> nodes, @NotNull SourceFile file, @NotNull Either<XY, Integer> location) {
    int low = 0;
    int high = nodes.size() - 1;

    while (low <= high) {
      int mid = (low + high) >>> 1;
      var node = nodes.get(mid);
      // TODO: use cache if GenericNode becomes UserDataHolder.
      var cmp = switch (location) {
        case Either.Left<XY, Integer>(var value) -> {
          var range = SourcePos.of(node.range(), file, false);
          yield -range.compareVisually(value.x(), value.y());
        }
        case Either.Right<XY, Integer>(var value) -> node.range().contains(value) ? 0
          : Integer.compare(node.range().getStartOffset(), value);
      };
      if (cmp == 0) return mid;
      if (cmp < 0) low = mid + 1;
      else high = mid - 1;
    }

    return -(low + 1);
  }

  /// All scope introducer with right-open, like [Pattern.Clause], but not [Expr.Param], does not include [org.aya.syntax.concrete.stmt.Stmt].
  /// These structure must/may start with a delimiter, such as `|`.
  public static final @NotNull TokenSet RIGHT_OPEN_INTRODUCER = TokenSet.create(
    AyaPsiElementTypes.BARRED_CLAUSE,
    AyaPsiElementTypes.LET_BIND_BLOCK,
    AyaPsiElementTypes.DATA_BODY
  );

  /// All possible start delimiter of [#RIGHT_OPEN_INTRODUCER],
  /// note that the token should be the direct child of those introducers.
  public static final @NotNull TokenSet RIGHT_OPEN_START_DELIMITERS = TokenSet.create(
    AyaPsiElementTypes.BAR
  );

  /// All possible end delimiters of any right close structure, such as `()`.
  /// [#refocus] cannot refocus to these structure
  /// [#refocus] also focus on an empty node even a _start delimiter_ is encountered.
  public static final @NotNull TokenSet RIGHT_CLOSE_DELIMITERS = AyaParserDefinitionBase.DELIMITERS;

  public static @NotNull GenericNode<?> rightMost(@NotNull GenericNode<?> node) {
    if (node.childrenView().isEmpty()) return node;
    return rightMost(node.lastChild());
  }

  /// Focus to another node nearby [#node], typically the left one.
  /// * the space between clauses/statements/let-bind/do-bind: left
  /// * the space between parameters: right/NOP
  /// * start delimiter of any right-open scope introducer (such as clause): left
  /// (for example, if `data Foo | a _| b`, then the cursor is inside `| a` rather than `| b`)
  ///
  /// @param node cannot be [EmptyNode]
  /// @return focused node, note that the returned node can be [EmptyNode]
  /// @apiNote It is possible that [#node] is returned while it is a [com.intellij.psi.PsiWhiteSpace]
  public static @NotNull GenericNode<?> refocus(@NotNull GenericNode<?> node) {
    var parent = node.parent();
    assert parent != null;

    var pparent = parent.parent();

    var siblings = pparent == null
      ? ImmutableSeq.<GenericNode<?>>empty()
      : pparent.childrenView().toSeq();

    var maybeAtNextRightOpen = RIGHT_OPEN_START_DELIMITERS.contains(node.elementType())
      && RIGHT_OPEN_INTRODUCER.contains(parent.elementType());
    var isWhiteSpace = node.elementType().equals(TokenType.WHITE_SPACE);

    if (maybeAtNextRightOpen) {
      var prevToken = prevToken(node);
      assert prevToken != null;
      return refocus(prevToken);
    }

    if (isWhiteSpace) {
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
      var offset = host.range().getStartOffset();

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
