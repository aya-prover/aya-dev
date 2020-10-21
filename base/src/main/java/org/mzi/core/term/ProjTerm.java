package org.mzi.core.term;

import org.jetbrains.annotations.NotNull;
import org.mzi.util.Decision;

/**
 * @author re-xyr
 */
public record ProjTerm(@NotNull Term tup, @NotNull int ix) implements Term {
  @Override
  public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitProj(this, p);
  }

  @Override
  public @NotNull Decision whnf() {
    return Decision.NO;
  }
}
