// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ide.action;

import com.intellij.psi.tree.IElementType;
import kala.collection.immutable.ImmutableSeq;
import org.aya.intellij.GenericNode;
import org.jetbrains.annotations.NotNull;

import java.util.function.ObjIntConsumer;

import static org.aya.ide.action.ContextWalker2.backward;

/// Split given [GenericNode#parent()] to `delimiters.size() + 1` part, and determine which part `node` lives in.
public record NodeLocator(@NotNull ImmutableSeq<IElementType> delimiters) {
  public NodeLocator(@NotNull IElementType... delimiters) {
    this(ImmutableSeq.from(delimiters));
  }

  public @NotNull ImmutableSeq<GenericNode<?>> locate(
    @NotNull GenericNode<?> node,
    @NotNull ObjIntConsumer<ImmutableSeq<GenericNode<?>>> callback
  ) {
    var prevSiblings = backward(node)
      .toSeq();

    int part = 0;   // also the index of the next delimiter
    for (var sibling : prevSiblings) {
      if (part == delimiters.size()) break;
      if (sibling.elementType() == delimiters.get(part)) {
        part++;
      }
    }

    callback.accept(prevSiblings, part);
    return prevSiblings;
  }
}
