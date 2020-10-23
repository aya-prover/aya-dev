package org.mzi.core.term;

import org.jetbrains.annotations.NotNull;
import org.mzi.generic.DTKind;
import org.mzi.generic.Tele;
import org.mzi.util.Decision;

/**
 * A (co)dependent type.
 *
 * @author ice1000
 */
public record DT(
  @NotNull Tele<Term> telescope,
  @NotNull Term last,
  @NotNull DTKind kind
) implements Term {
  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitDT(this, p);
  }

  @Override public <P, Q, R> R accept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
    return visitor.visitDT(this, p, q);
  }

  @Override public @NotNull Decision whnf() {
    return Decision.YES;
  }
}
