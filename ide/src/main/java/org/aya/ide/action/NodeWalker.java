// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ide.action;

import com.intellij.psi.tree.TokenSet;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.FreezableMutableList;
import kala.control.Either;
import org.aya.ide.util.XY;
import org.aya.intellij.GenericNode;
import org.aya.util.Panic;
import org.aya.util.position.SourceFile;
import org.aya.util.position.SourcePos;
import org.jetbrains.annotations.NotNull;

public class NodeWalker {
  public record Result(@NotNull ImmutableSeq<GenericNode<?>> nodeChain) { }

  private final @NotNull Either<XY, Integer> location;
  private final @NotNull SourceFile file;
  private final @NotNull GenericNode<?> node;
  private final @NotNull TokenSet leafTypes;

  public NodeWalker(@NotNull SourceFile file, @NotNull GenericNode<?> node, @NotNull Either<XY, Integer> location, @NotNull TokenSet leafTypes) {
    this.location = location;
    this.file = file;
    this.node = node;
    this.leafTypes = leafTypes;
  }

  public NodeWalker(@NotNull SourceFile file, @NotNull GenericNode<?> node, @NotNull XY location) {
    this.node = node;
    this.file = file;
    this.location = Either.left(location);
    this.leafTypes = TokenSet.EMPTY;
  }

  /// Find the **node** under [#location], note that it could be empty (such as [com.intellij.psi.PsiWhiteSpace])
  public @NotNull Result run() {
    var nodeChain = FreezableMutableList.<GenericNode<?>>create();
    var node = this.node;

    while (true) {
      nodeChain.append(node);

      if (leafTypes.contains(node.elementType())) break;

      var children = ImmutableSeq.<GenericNode<?>>narrow(node.childrenView().toSeq());
      if (children.isEmpty()) break;

      var idx = binarySearch(children, file, location);
      // normally [idx] won't be negative, as the [TextRange]s of children are continuous.
      if (idx < 0) Panic.unreachable();   // TODO: what should i do?
      node = children.get(idx);
    }

    return new Result(nodeChain.freeze());
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
}
