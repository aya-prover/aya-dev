package org.mzi.concrete.visitor;

import asia.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;
import org.mzi.concrete.term.*;

public interface BaseExprVisitor<P> extends Expr.Visitor<P, @NotNull Expr>, Param.Visitor<P, @NotNull ImmutableSeq<Param>> {
  @Override
  default @NotNull Expr visitRef(RefExpr refExpr, P p) {
    return refExpr;
  }

  @Override
  default @NotNull Expr visitUnresolved(@NotNull UnresolvedExpr expr, P p) {
    return expr;
  }

  @Override
  default @NotNull Expr visitLam(@NotNull LamExpr expr, P p) {
    // TODO[xyr]: This line below looks very strange. Any way to fix it?
    var binds = visitParams(expr.binds(), p);
    var body = expr.body().accept(this, p);
    if (binds == expr.binds() && body == expr.body()) return expr;
    return new LamExpr(binds, body);
  }

  @Override
  default @NotNull Expr visitPi(@NotNull PiExpr expr, P p) {
    // TODO[xyr]: also here.
    var binds = visitParams(expr.binds(), p);
    var body = expr.body().accept(this, p);
    if (binds == expr.binds() && body == expr.body()) return expr;
    return new PiExpr(binds, body);
  }

  @Override
  default @NotNull Expr visitUniv(@NotNull UnivExpr expr, P p) {
    return expr;
  }

  // TODO[xyr]: or visitArgs(ImmutableSeq<Arg>)?
  default @NotNull AppExpr.Arg visitArg(@NotNull AppExpr.Arg arg, P p) {
    var term = arg.expr().accept(this, p);
    if (term == arg.expr()) return arg;
    return new AppExpr.Arg(term, arg.explicit());
  }

  @Override
  default @NotNull Expr visitApp(@NotNull AppExpr expr, P p) {
    var function = expr.function().accept(this, p);
    var arg = expr.argument().map(x -> visitArg(x, p));
    if (function == expr.function() && arg == expr.argument()) return expr;
    return new AppExpr(function, arg);
  }
}
