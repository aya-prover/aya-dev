package org.aya.parser.ij;

import com.intellij.AyaModified;
import com.intellij.psi.builder.ASTMarkerVisitor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import kala.collection.SeqView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Generalized {@link ASTMarkerVisitor.Node} for reusing psi interfaces in Producer */
@AyaModified
public interface GenericNode<N extends GenericNode<N>> {
  @NotNull IElementType elementType();
  @NotNull String tokenText();
  int startOffset();
  int endOffset();
  boolean isTerminalNode();
  @NotNull SeqView<N> childrenView();

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
