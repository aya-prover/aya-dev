// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.visitor;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.Expr;
import org.jetbrains.annotations.NotNull;

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
    return new Expr.HoleExpr(expr.sourcePos(), expr.explicit(), h);
  }

  default @NotNull ImmutableSeq<Expr.@NotNull Param> visitParams(@NotNull ImmutableSeq<Expr.@NotNull Param> params, P p) {
    return params.map(param -> {
      var oldType = param.type();
      var type = oldType.accept(this, p);
      if (type == oldType) return param;
      return new Expr.Param(param, type);
    });
  }

  @Override default @NotNull Expr visitLam(Expr.@NotNull LamExpr expr, P p) {
    var bind = visitParams(ImmutableSeq.of(expr.param()), p).get(0);
    var body = expr.body().accept(this, p);
    if (bind == expr.param() && body == expr.body()) return expr;
    return new Expr.LamExpr(expr.sourcePos(), bind, body);
  }

  @Override default @NotNull Expr visitError(Expr.@NotNull ErrorExpr error, P p) {
    return error;
  }

  @Override default @NotNull Expr visitPi(Expr.@NotNull PiExpr expr, P p) {
    var bind = visitParams(ImmutableSeq.of(expr.param()), p).get(0);
    var body = expr.last().accept(this, p);
    if (bind == expr.param() && body == expr.last()) return expr;
    return new Expr.PiExpr(expr.sourcePos(), expr.co(), bind, body);
  }

  @Override default @NotNull Expr visitSigma(Expr.@NotNull SigmaExpr expr, P p) {
    var binds = visitParams(expr.params(), p);
    if (binds.sameElements(expr.params(), true)) return expr;
    return new Expr.SigmaExpr(expr.sourcePos(), expr.co(), binds);
  }

  @Override default @NotNull Expr visitRawUniv(Expr.@NotNull RawUnivExpr expr, P p) {
    return expr;
  }

  @Override default @NotNull Expr visitRawUnivArgs(Expr.@NotNull RawUnivArgsExpr expr, P p) {
    var args = expr.univArgs().map(e -> e.accept(this, p));
    if (args.sameElements(expr.univArgs(), true)) return expr;
    return new Expr.RawUnivArgsExpr(expr.sourcePos(), args);
  }

  @Override default @NotNull Expr visitUnivArgs(Expr.@NotNull UnivArgsExpr expr, P p) {
    return expr;
  }

  @Override default @NotNull Expr visitUniv(Expr.@NotNull UnivExpr expr, P p) {
    return expr;
  }

  @Override default @NotNull Expr visitApp(Expr.@NotNull AppExpr expr, P p) {
    var function = expr.function().accept(this, p);
    var argument = expr.argument();
    var argExpr = argument.expr().accept(this, p);
    if (function == expr.function() && argExpr == argument.expr()) return expr;
    var newArg = new Expr.NamedArg(argument.explicit(), argument.name(), argExpr);
    return new Expr.AppExpr(expr.sourcePos(), function, newArg);
  }

  @Override default @NotNull Expr visitTup(Expr.@NotNull TupExpr expr, P p) {
    var items = expr.items().map(item -> item.accept(this, p));
    if (items.sameElements(expr.items(), true)) return expr;
    return new Expr.TupExpr(expr.sourcePos(), items);
  }

  @Override default @NotNull Expr visitProj(Expr.@NotNull ProjExpr expr, P p) {
    var tup = expr.tup().accept(this, p);
    if (tup == expr.tup()) return expr;
    return new Expr.ProjExpr(expr.sourcePos(), tup, expr.ix(), expr.resolvedIx(), expr.theCore());
  }

  @Override default @NotNull Expr visitLsuc(Expr.@NotNull LSucExpr expr, P p) {
    var lvl = expr.expr().accept(this, p);
    if (lvl == expr.expr()) return expr;
    return new Expr.LSucExpr(expr.sourcePos(), lvl);
  }

  @Override default @NotNull Expr visitLmax(Expr.@NotNull LMaxExpr expr, P p) {
    var fields = expr.levels().map(t -> t.accept(this, p));
    if (fields.sameElements(expr.levels(), true)) return expr;
    return new Expr.LMaxExpr(expr.sourcePos(), fields);
  }

  @Override default @NotNull Expr visitNew(Expr.@NotNull NewExpr expr, P p) {
    var struct = expr.struct().accept(this, p);
    var fields = expr.fields().map(t -> visitField(t, p));
    if (expr.struct() == struct && fields.sameElements(expr.fields(), true)) return expr;
    return new Expr.NewExpr(expr.sourcePos(), struct, fields);
  }

  default Expr.@NotNull Field visitField(@NotNull Expr.Field t, P p) {
    var accept = t.body().accept(this, p);
    if (accept == t.body()) return t;
    return new Expr.Field(t.name(), t.bindings(), accept);
  }

  @Override default @NotNull Expr visitLitInt(Expr.@NotNull LitIntExpr expr, P p) {
    return expr;
  }

  @Override default @NotNull Expr visitLitString(Expr.@NotNull LitStringExpr expr, P p) {
    return expr;
  }

  @Override default @NotNull Expr visitBinOpSeq(Expr.@NotNull BinOpSeq binOpSeq, P p) {
    return new Expr.BinOpSeq(binOpSeq.sourcePos(),
      binOpSeq.seq().map(e -> new Expr.NamedArg(e.explicit(), e.name(), e.expr().accept(this, p))));
  }
}
