package org.mzi.core.term;

import asia.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;
import org.mzi.core.tele.Tele;
import org.mzi.core.tele.Telescopic;

/**
 * @author ice1000
 */
public record LamTerm(@NotNull Tele telescope, @NotNull Term body) implements Term, Telescopic {
  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitLam(this, p);
  }
}
