package org.mzi.concrete.term;

import org.jetbrains.annotations.NotNull;

/**
 * @author re-xyr
 */
public interface Term {
  <P, R> R accept(@NotNull Visitor<P, R> visitor, P p);

  interface Visitor<P, R> {
    R visitRef(@NotNull RefTerm term, P p);
    R visitLam(@NotNull LamTerm term, P p);
    R visitPi(@NotNull PiTerm term, P p);
    R visitUniv(@NotNull UnivTerm term, P p);
    R visitApp(@NotNull AppTerm.Apply term, P p);
  }
}
