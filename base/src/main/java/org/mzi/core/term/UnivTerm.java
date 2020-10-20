package org.mzi.core.term;

import org.jetbrains.annotations.NotNull;
import org.mzi.util.Decision;

/**
 * @author ice1000
 */
// TODO: sort system
public record UnivTerm() implements Term {
  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitUniv(this, p);
  }

  @Override public @NotNull Decision whnf() {
    return Decision.YES;
  }
}
