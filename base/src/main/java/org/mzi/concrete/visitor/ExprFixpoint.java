package org.mzi.concrete.visitor;

import asia.kala.collection.Seq;
import asia.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;
import org.mzi.concrete.term.*;
import org.mzi.generic.Arg;

public interface ExprFixpoint<P> extends Expr.Visitor<P, @NotNull Expr> {
  @Override default @NotNull Expr visitRef(@NotNull RefExpr refExpr, P p) {
    return refExpr;
  }

  @Override default @NotNull Expr visitUnresolved(@NotNull UnresolvedExpr expr, P p) {
    return expr;
  }

  @Override default @NotNull Expr visitLam(@NotNull LamExpr expr, P p) {
    var binds = visitParams(expr.binds(), p);
    var body = expr.body().accept(this, p);
    if (binds == expr.binds() && body == expr.body()) return expr;
    return new LamExpr(binds, body);
  }

  default @NotNull ImmutableSeq<@NotNull Param>
  visitParams(@NotNull ImmutableSeq<@NotNull Param> binds, P p) {
    var newBinds = binds.map(param -> visitParam(param, p));
    if (newBinds.sameElements(binds, true)) return binds;
    else return newBinds;
  }

  default @NotNull Param visitParam(@NotNull Param param, P p) {
    var type = param.type().accept(this, p);
    if (type == param.type()) return param;
    else return new Param(param.ref(), type, param.explicit());
  }

  @Override default @NotNull Expr visitDT(@NotNull DTExpr expr, P p) {
    var binds = visitParams(expr.binds(), p);
    if (binds == expr.binds()) return expr;
    return new DTExpr(binds, expr.kind());
  }

  @Override default @NotNull Expr visitUniv(@NotNull UnivExpr expr, P p) {
    return expr;
  }

  default @NotNull Arg<Expr> visitArg(@NotNull Arg<Expr> arg, P p) {
    var term = arg.term().accept(this, p);
    if (term == arg.term()) return arg;
    return new Arg<>(term, arg.explicit());
  }

  @Override default @NotNull Expr visitApp(@NotNull AppExpr expr, P p) {
    var function = expr.function().accept(this, p);
    var arg = expr.argument().map(x -> visitArg(x, p));
    if (function == expr.function() && arg.sameElements(expr.argument(), true)) return expr;
    return new AppExpr(function, arg);
  }
}
