// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.desugar;

import org.glavo.kala.Unit;
import org.jetbrains.annotations.NotNull;
import org.mzi.concrete.Expr;
import org.mzi.concrete.visitor.ExprFixpoint;

public final class ExprDesugarer implements ExprFixpoint<Unit> {
  public static final ExprDesugarer INSTANCE = new ExprDesugarer();

  private @NotNull Expr makeNestingLam(Expr.@NotNull TelescopicLamExpr expr, int pos) {
    if (pos == expr.params().size()) return expr.body().accept(this, Unit.unit());
    return new Expr.LamExpr(expr.sourcePos(), expr.params().get(pos), makeNestingLam(expr, pos + 1));
  }

  @Override
  public @NotNull Expr visitTelescopicLam(Expr.@NotNull TelescopicLamExpr expr, Unit u) {
    return makeNestingLam(expr, 0);
  }

  private @NotNull Expr makeNestingPi(Expr.@NotNull TelescopicPiExpr expr, int pos) {
    if (pos == expr.params().size()) return expr.last().accept(this, Unit.unit());
    return new Expr.PiExpr(expr.sourcePos(), expr.co(), expr.params().get(pos), makeNestingPi(expr, pos + 1));
  }

  @Override
  public @NotNull Expr visitTelescopicPi(Expr.@NotNull TelescopicPiExpr expr, Unit u) {
    return makeNestingPi(expr, 0);
  }

  private ExprDesugarer() {}
}
