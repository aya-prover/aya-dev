package org.mzi.core.term;

import org.jetbrains.annotations.NotNull;
import org.mzi.core.subst.TermSubst;

/**
 * @author ice1000
 */
public interface Term {
  <P, R> R accept(@NotNull Visitor<P, R> visitor, P p);
  default @NotNull Term subst(@NotNull TermSubst subst) {
    throw new UnsupportedOperationException();
  }

  interface Visitor<P, R> {
    R visitRef(@NotNull RefTerm term, P p);
    R visitLam(@NotNull LamTerm term, P p);
    R visitPi(@NotNull PiTerm term, P p);
    R visitUniv(@NotNull UnivTerm term, P p);
  }
}
