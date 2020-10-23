package org.mzi.concrete.visitor;

import asia.kala.EmptyTuple;
import asia.kala.Tuple;
import asia.kala.control.Option;
import org.jetbrains.annotations.NotNull;
import org.mzi.concrete.Expr;
import org.mzi.concrete.Param;
import org.mzi.generic.Arg;
import org.mzi.generic.Tele;

public interface ExprConsumer<P> extends Expr.Visitor<P, EmptyTuple>, Tele.Visitor<Expr, P, EmptyTuple> {
  @Override default EmptyTuple visitNamed(Tele.@NotNull NamedTele<Expr> named, P p) {
    return named.next().accept(this, p);
  }

  @Override default EmptyTuple visitTyped(Tele.@NotNull TypedTele<Expr> typed, P p) {
    Option.of(typed.next()).forEach(tele -> tele.accept(this, p));
    return typed.type().accept(this, p);
  }

  @Override default EmptyTuple visitRef(Expr.@NotNull RefExpr expr, P p) {
    return Tuple.empty();
  }

  @Override default EmptyTuple visitUnresolved(Expr.@NotNull UnresolvedExpr expr, P p) {
    return Tuple.empty();
  }

  @Override
  default EmptyTuple visitHole(Expr.@NotNull HoleExpr holeExpr, P p) {
    var expr = holeExpr.filling();
    if (expr != null) expr.accept(this, p);
    return Tuple.empty();
  }

  @Override default EmptyTuple visitUniv(Expr.@NotNull UnivExpr expr, P p) {
    return Tuple.empty();
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
    expr.tele().accept(this, p);
    return Tuple.empty();
  }

  @Override default EmptyTuple visitLam(Expr.@NotNull LamExpr expr, P p) {
    expr.tele().accept(this, p);
    return expr.body().accept(this, p);
  }
}
