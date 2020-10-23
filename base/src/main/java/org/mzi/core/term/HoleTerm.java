package org.mzi.core.term;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.mzi.util.Decision;

/**
 * @author ice1000
 */
public record HoleTerm(
  // TODO
) implements Term {
  @Override
  public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitHole(this, p);
  }

  @Contract(pure = true) @Override public @NotNull Decision whnf() {
    return Decision.MAYBE;
  }
}
