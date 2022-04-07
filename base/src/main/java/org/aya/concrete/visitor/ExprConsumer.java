// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.visitor;

import kala.collection.immutable.ImmutableSeq;
import kala.tuple.Unit;
import org.aya.concrete.Expr;
import org.jetbrains.annotations.NotNull;

public interface ExprConsumer<P> extends Expr.Visitor<P, Unit> {
  @Override default Unit visitRef(Expr.@NotNull RefExpr expr, P p) {
    return Unit.unit();
  }

  @Override default Unit visitUnresolved(Expr.@NotNull UnresolvedExpr expr, P p) {
    return Unit.unit();
  }

  @Override default Unit visitError(Expr.@NotNull ErrorExpr error, P p) {
    return Unit.unit();
  }

  @Override default Unit visitHole(Expr.@NotNull HoleExpr holeExpr, P p) {
    var expr = holeExpr.filling();
    if (expr != null) expr.accept(this, p);
    return Unit.unit();
  }

  @Override default Unit visitBinOpSeq(Expr.@NotNull BinOpSeq binOpSeq, P p) {
    binOpSeq.seq().forEach(e -> e.expr().accept(this, p));
    return Unit.unit();
  }

  @Override default Unit visitNew(Expr.@NotNull NewExpr expr, P p) {
    expr.fields().forEach(e -> e.body().accept(this, p));
    return expr.struct().accept(this, p);
  }

  @Override default Unit visitUniv(Expr.@NotNull UnivExpr expr, P p) {
    return Unit.unit();
  }

  @Override default Unit visitMetaPat(Expr.@NotNull MetaPat metaPat, P p) {
    return Unit.unit();
  }

  @Override default Unit visitLift(Expr.@NotNull LiftExpr expr, P p) {
    return expr.expr().accept(this, p);
  }

  @Override default Unit visitRawUniv(Expr.@NotNull RawUnivExpr expr, P p) {
    return Unit.unit();
  }

  @Override
  default Unit visitInterval(Expr.@NotNull IntervalExpr expr, P p) {
    return Unit.unit();
  }
  @Override default Unit visitApp(Expr.@NotNull AppExpr expr, P p) {
    expr.argument().expr().accept(this, p);
    return expr.function().accept(this, p);
  }

  default void visitParams(@NotNull ImmutableSeq<Expr.@NotNull Param> params, P p) {
    params.forEach(param -> param.type().accept(this, p));
  }

  @Override default Unit visitLam(Expr.@NotNull LamExpr expr, P p) {
    visitParams(ImmutableSeq.of(expr.param()), p);
    return expr.body().accept(this, p);
  }

  @Override default Unit visitPi(Expr.@NotNull PiExpr expr, P p) {
    visitParams(ImmutableSeq.of(expr.param()), p);
    return expr.last().accept(this, p);
  }

  @Override default Unit visitSigma(Expr.@NotNull SigmaExpr expr, P p) {
    visitParams(expr.params(), p);
    return Unit.unit();
  }

  @Override default Unit visitTup(Expr.@NotNull TupExpr expr, P p) {
    expr.items().forEach(item -> item.accept(this, p));
    return Unit.unit();
  }

  @Override default Unit visitProj(Expr.@NotNull ProjExpr expr, P p) {
    return expr.tup().accept(this, p);
  }

  @Override default Unit visitLitInt(Expr.@NotNull LitIntExpr expr, P p) {
    return Unit.unit();
  }

  default void visitTacNode(@NotNull Expr.TacNode node, P p) {
    switch (node) {
      case Expr.TacNode.ExprTac expr -> expr.expr().accept(this, p);
      case Expr.TacNode.ListExprTac list -> list.tacNodes().forEach(n -> visitTacNode(n, p));
    }
  }

  @Override default Unit visitTac(Expr.@NotNull TacExpr tactic, P p) {
    visitTacNode(tactic.tacNode(), p);
    return Unit.unit();
  }

  @Override default Unit visitLitString(Expr.@NotNull LitStringExpr expr, P p) {
    return Unit.unit();
  }
}
