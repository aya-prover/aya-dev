// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.parser;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import kala.collection.SeqView;
import kala.text.StringSlice;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Generalized {@link com.intellij.psi.builder.MarkerNode} for reusing psi interfaces in Producer */
public interface GenericNode<N extends GenericNode<N>> {
  @NotNull IElementType elementType();
  @NotNull StringSlice tokenText();
  @NotNull TextRange range();
  default boolean isTerminalNode() {
    return elementType() instanceof AyaPsiTokenType;
  }
  @NotNull SeqView<N> childrenView();
  default @NotNull @NonNls String toDebugString() {
    return toString();
  }

  default boolean is(@NotNull IElementType type) {
    return elementType() == type;
  }

  default boolean is(@NotNull TokenSet tokenSet) {
    return tokenSet.contains(elementType());
  }

  default @NotNull SeqView<N> childrenOfType(@NotNull IElementType type) {
    return childrenView().filter(c -> c.is(type));
  }

  default @NotNull SeqView<N> childrenOfType(@NotNull TokenSet tokenSet) {
    return childrenView().filter(c -> c.is(tokenSet));
  }

  default @Nullable N peekChild(@NotNull IElementType type) {
    return childrenOfType(type).firstOrNull();
  }

  default @Nullable N peekChild(@NotNull TokenSet tokenSet) {
    return childrenOfType(tokenSet).firstOrNull();
  }

  default @NotNull N child(@NotNull IElementType type) {
    return Objects.requireNonNull(peekChild(type));
  }

  default @NotNull N child(@NotNull TokenSet tokenSet) {
    return Objects.requireNonNull(peekChild(tokenSet));
  }
}
