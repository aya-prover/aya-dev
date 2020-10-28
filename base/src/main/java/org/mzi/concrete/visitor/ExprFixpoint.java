// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.visitor;

import asia.kala.control.Option;
import org.jetbrains.annotations.NotNull;
import org.mzi.concrete.Expr;
import org.mzi.generic.Arg;
import org.mzi.core.Tele;

public interface ExprFixpoint<P> extends
  Expr.Visitor<P, @NotNull Expr>,
  Tele.Visitor<Expr, P, @NotNull Tele<Expr>> {
  @Override default @NotNull Tele<Expr> visitNamed(Tele.@NotNull NamedTele<Expr> named, P p) {
    var next = named.next().accept(this, p);
    if (next == named.next()) return named;
    return new Tele.NamedTele<>(named.ref(), next);
  }

  @Override default @NotNull Tele<Expr> visitTyped(Tele.@NotNull TypedTele<Expr> typed, P p) {
    var next = Option.of(typed.next()).map(tele -> tele.accept(this, p)).getOrNull();
    var type = typed.type().accept(this, p);
    if (next == typed.next() && type == typed.type()) return typed;
    return new Tele.TypedTele<>(typed.ref(), type, typed.explicit(), next);
  }

  @Override default @NotNull Expr visitRef(Expr.@NotNull RefExpr expr, P p) {
    return expr;
  }

  @Override default @NotNull Expr visitUnresolved(Expr.@NotNull UnresolvedExpr expr, P p) {
    return expr;
  }

  @Override default @NotNull Expr visitHole(Expr.@NotNull HoleExpr expr, P p) {
    var h = expr.filling() != null ? expr.filling().accept(this, p) : null;
    if (h == expr.filling()) return expr;
    return new Expr.HoleExpr(expr.sourcePos(), expr.name(), h);
  }

  @Override default @NotNull Expr visitLam(Expr.@NotNull LamExpr expr, P p) {
    var binds = expr.tele().accept(this, p);
    var body = expr.body().accept(this, p);
    if (binds == expr.tele() && body == expr.body()) return expr;
    return new Expr.LamExpr(expr.sourcePos(), binds, body);
  }

  @Override default @NotNull Expr visitDT(Expr.@NotNull DTExpr expr, P p) {
    var binds = expr.tele().accept(this, p);
    var last = expr.last().accept(this, p);
    if (binds == expr.tele() && last == expr.last()) return expr;
    return new Expr.DTExpr(expr.sourcePos(), binds, last, expr.kind());
  }

  @Override default @NotNull Expr visitUniv(Expr.@NotNull UnivExpr expr, P p) {
    return expr;
  }

  default @NotNull Arg<Expr> visitArg(@NotNull Arg<Expr> arg, P p) {
    var term = arg.term().accept(this, p);
    if (term == arg.term()) return arg;
    return new Arg<>(term, arg.explicit());
  }

  @Override default @NotNull Expr visitApp(Expr.@NotNull AppExpr expr, P p) {
    var function = expr.function().accept(this, p);
    var arg = expr.argument().map(x -> visitArg(x, p));
    if (function == expr.function() && arg.sameElements(expr.argument(), true)) return expr;
    return new Expr.AppExpr(expr.sourcePos(), function, arg);
  }

  @Override default @NotNull Expr visitTup(Expr.@NotNull TupExpr expr, P p) {
    var items = expr.items().map(item -> item.accept(this, p));
    if (items.sameElements(expr.items(), true)) return expr;
    return new Expr.TupExpr(expr.sourcePos(), items);
  }

  @Override default @NotNull Expr visitProj(Expr.@NotNull ProjExpr expr, P p) {
    var tup = expr.tup().accept(this, p);
    if (tup == expr.tup()) return expr;
    return new Expr.ProjExpr(expr.sourcePos(), tup, expr.ix());
  }
}
