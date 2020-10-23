package org.mzi.core.term;

import org.jetbrains.annotations.NotNull;
import org.mzi.generic.Tele;
import org.mzi.util.Decision;

/**
 * @author ice1000
 */
public record LamTerm(@NotNull Tele<Term> tele, @NotNull Term body) implements Term {
  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitLam(this, p);
  }

  @Override public <P, Q, R> R accept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
    return visitor.visitLam(this, p, q);
  }

  @Override public @NotNull Decision whnf() {
    return Decision.YES;
  }
}
