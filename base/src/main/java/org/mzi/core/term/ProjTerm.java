package org.mzi.core.term;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.mzi.util.Decision;

/**
 * @author re-xyr
 */
public record ProjTerm(@NotNull Term tup, int ix) implements Term {
  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitProj(this, p);
  }

  @Override public <P, Q, R> R accept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
    return visitor.visitProj(this, p, q);
  }

  @Contract(pure = true) @Override public @NotNull Decision whnf() {
    if (tup instanceof TupTerm) return Decision.NO;
    else return tup.whnf();
  }
}
