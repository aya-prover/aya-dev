package org.mzi.core.term;

import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public interface Term {
  <P, R> R accept(@NotNull Visitor<P, R> visitor, P p);

  interface Visitor<P, R> {
    R visitRef(@NotNull RefTerm term, P p);
    R visitLam(@NotNull LamTerm lamTerm, P p);
    R visitPi(@NotNull PiTerm piTerm, P p);
    R visitUniv(@NotNull UnivTerm univTerm, P p);
  }
}
