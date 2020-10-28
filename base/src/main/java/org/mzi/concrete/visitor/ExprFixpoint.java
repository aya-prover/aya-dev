// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.visitor;

import asia.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.NotNull;
import org.mzi.concrete.Expr;
import org.mzi.concrete.Param;
import org.mzi.generic.Arg;

public interface ExprFixpoint<P> extends Expr.Visitor<P, @NotNull Expr> {
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

  default @NotNull Buffer<Param> visitParams(@NotNull Buffer<Param> params, P p) {
    return params.view().map(param -> {
      var type = param.type().accept(this, p);
      if (type == param.type()) return param;
      return new Param(param.sourcePos(), param.vars(), type, param.explicit());
    }).collect(Buffer.factory());
  }

  @Override default @NotNull Expr visitLam(Expr.@NotNull LamExpr expr, P p) {
    var binds = visitParams(expr.params(), p);
    var body = expr.body().accept(this, p);
    if (binds.sameElements(expr.params(), true) && body == expr.body()) return expr;
    return new Expr.LamExpr(expr.sourcePos(), binds, body);
  }

  @Override default @NotNull Expr visitDT(Expr.@NotNull DTExpr expr, P p) {
    var binds = visitParams(expr.params(), p);
    var last = expr.last().accept(this, p);
    if (binds.sameElements(expr.params(), true) && last == expr.last()) return expr;
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
