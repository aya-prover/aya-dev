package org.mzi.concrete.term;

import org.jetbrains.annotations.NotNull;

/**
 * @author re-xyr
 */
public sealed interface Expr permits
  AppExpr,
  LamExpr,
  DTExpr,
  RefExpr,
  UnivExpr,
  UnresolvedExpr {
  <P, R> R accept(@NotNull Visitor<P, R> visitor, P p);

  interface Visitor<P, R> {
    R visitRef(@NotNull RefExpr refExpr, P p);
    R visitUnresolved(@NotNull UnresolvedExpr expr, P p);
    R visitLam(@NotNull LamExpr expr, P p);
    R visitDT(@NotNull DTExpr expr, P p);
    R visitUniv(@NotNull UnivExpr expr, P p);
    R visitApp(@NotNull AppExpr expr, P p);
  }
}
