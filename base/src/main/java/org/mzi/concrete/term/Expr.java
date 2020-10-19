package org.mzi.concrete.term;

import org.jetbrains.annotations.NotNull;

/**
 * @author re-xyr
 */
public interface Expr {
  <P, R> R accept(@NotNull Visitor<P, R> visitor, P p);

  interface Visitor<P, R> {
    R visitRef(@NotNull RefExpr term, P p);
    R visitLam(@NotNull LamExpr term, P p);
    R visitPi(@NotNull PiExpr term, P p);
    R visitUniv(@NotNull UnivExpr term, P p);
    R visitApp(@NotNull AppExpr term, P p);
  }
}
