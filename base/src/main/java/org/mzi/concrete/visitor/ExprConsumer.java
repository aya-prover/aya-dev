package org.mzi.concrete.visitor;

import asia.kala.EmptyTuple;
import asia.kala.Tuple;
import asia.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;
import org.mzi.concrete.Expr;
import org.mzi.concrete.Param;
import org.mzi.generic.Arg;

public interface ExprConsumer<P> extends Expr.Visitor<P, EmptyTuple> {
  @Override default EmptyTuple visitRef(Expr.@NotNull RefExpr refExpr, P p) {
    return Tuple.of();
  }

  @Override default EmptyTuple visitUnresolved(Expr.@NotNull UnresolvedExpr expr, P p) {
    return Tuple.of();
  }

  @Override
  default EmptyTuple visitHole(Expr.@NotNull HoleExpr holeExpr, P p) {
    var expr = holeExpr.holeExpr();
    if (expr != null) expr.accept(this, p);
    return Tuple.of();
  }

  @Override default EmptyTuple visitUniv(Expr.@NotNull UnivExpr expr, P p) {
    return Tuple.of();
  }

  default void visitArg(@NotNull Arg<Expr> arg, P p) {
    arg.term().accept(this, p);
  }

  @Override default EmptyTuple visitApp(Expr.@NotNull AppExpr expr, P p) {
    expr.argument().forEach(arg -> visitArg(arg, p));
    return expr.function().accept(this, p);
  }

  default void visitParam(@NotNull Param param, P p) {
    param.type().accept(this, p);
  }

  @Override default EmptyTuple visitDT(Expr.@NotNull DTExpr expr, P p) {
    visitBinds(p, expr.binds());
    return Tuple.of();
  }

  @Override default EmptyTuple visitLam(Expr.@NotNull LamExpr expr, P p) {
    visitBinds(p, expr.binds());
    return expr.body().accept(this, p);
  }

  default void visitBinds(P p, @NotNull ImmutableSeq<@NotNull Param> binds) {
    binds.forEach(param -> visitParam(param, p));
  }
}
