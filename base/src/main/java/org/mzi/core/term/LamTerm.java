package org.mzi.core.term;

import asia.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;
import org.mzi.core.ref.Bind;

/**
 * @author ice1000
 */
public record LamTerm(@NotNull ImmutableSeq<@NotNull Bind> binds, @NotNull Term body) implements Term {
  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitLam(this, p);
  }
}
