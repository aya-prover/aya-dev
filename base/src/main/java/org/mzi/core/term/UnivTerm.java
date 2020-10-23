package org.mzi.core.term;

import org.jetbrains.annotations.NotNull;
import org.mzi.tyck.sort.Sort;
import org.mzi.util.Decision;

/**
 * @author ice1000
 */
public record UnivTerm(@NotNull Sort sort) implements Term {
  public static final /*@NotNull*/ UnivTerm OMEGA = new UnivTerm(Sort.OMEGA);

  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitUniv(this, p);
  }

  @Override public <P, Q, R> R accept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
    return visitor.visitUniv(this, p, q);
  }

  @Override public @NotNull Decision whnf() {
    return Decision.YES;
  }
}
