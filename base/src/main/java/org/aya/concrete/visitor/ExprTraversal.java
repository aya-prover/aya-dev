// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.visitor;

import org.aya.concrete.Expr;
import org.jetbrains.annotations.NotNull;

public interface ExprTraversal<P> {
  default @NotNull Expr visitExpr(@NotNull Expr expr, P p) {
    switch (expr) {
      case Expr.AppExpr app -> {
        visitExpr(app.function(), p);
        visitExpr(app.argument().expr(), p);
      }
      case Expr.NewExpr neo -> {
        neo.fields().forEach(e -> visitExpr(e.body(), p));
        visitExpr(neo.struct(), p);
      }
      case Expr.BinOpSeq seq -> seq.seq().forEach(e -> visitExpr(e.expr(), p));
      case Expr.SigmaExpr sig -> sig.params().forEach(e -> visitParam(e, p));
      case Expr.LamExpr lamExpr -> {
        visitParam(lamExpr.param(), p);
        visitExpr(lamExpr.body(), p);
      }
      case Expr.TupExpr tup -> tup.items().forEach(i -> visitExpr(i, p));
      case Expr.ProjExpr proj -> visitExpr(proj.tup(), p);
      case Expr.LiftExpr lift -> visitExpr(lift.expr(), p);
      case Expr.HoleExpr hole -> {
        if (hole.filling() != null) visitExpr(hole.filling(), p);
      }
      case Expr.PiExpr pi -> {
        visitParam(pi.param(), p);
        visitExpr(pi.last(), p);
      }
      case Expr.PartEl el -> el.partial().forEach(e -> visitExpr(e, p));
      case Expr.PartTy ty -> {
        ty.restr().instView().forEach(e -> visitExpr(e, p));
        visitExpr(ty.type(), p);
      }
      case Expr.Path path -> {
        visitExpr(path.cube().type(), p);
        path.cube().partial().forEach(e -> visitExpr(e, p));
      }
      default -> {}
    }
    return expr;
  }

  default @NotNull Expr visitParam(Expr.Param e, P pp) {
    return visitExpr(e.type(), pp);
  }
}
