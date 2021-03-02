// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.mzi.concrete.resolve.visitor;

import org.glavo.kala.collection.SeqLike;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.tuple.Tuple2;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.mzi.concrete.Expr;
import org.mzi.concrete.resolve.context.Context;
import org.mzi.concrete.visitor.ExprFixpoint;

/**
 * Resolves bindings.
 *
 * @author re-xyr
 */
public final class ExprResolver implements ExprFixpoint<Context> {
  public static final @NotNull ExprResolver INSTANCE = new ExprResolver();

  private ExprResolver() {
  }

  @Override public @NotNull Expr visitUnresolved(@NotNull Expr.UnresolvedExpr expr, Context ctx) {
    return new Expr.RefExpr(expr.sourcePos(), ctx.getUnqualified(expr.name(), expr.sourcePos()));
  }

  public @NotNull Tuple2<Expr.Param, Context> visitParam(@NotNull Expr.Param param, Context ctx) {
    var type = param.type();
    type = type == null ? null : type.accept(this, ctx);
    return Tuple2.of(
      new Expr.Param(param.sourcePos(), param.ref(), type, param.explicit()),
      ctx.bind(param.ref().name(), param.ref(), param.sourcePos())
    );
  }

  @Contract(pure = true)
  public @NotNull Tuple2<ImmutableSeq<Expr.Param>, Context>
  resolveParams(@NotNull SeqLike<Expr.Param> params, Context ctx) {
    if (params.isEmpty()) return Tuple2.of(ImmutableSeq.of(), ctx);
    var first = params.first();
    var type = first.type();
    type = type == null ? null : type.accept(this, ctx);
    var newCtx = ctx.bind(first.ref().name(), first.ref(), first.sourcePos());
    var result = resolveParams(params.view().drop(1), newCtx);
    return Tuple2.of(
      result._1.prepended(new Expr.Param(first.sourcePos(), first.ref(), type, first.explicit())),
      result._2
    );
  }

  @Override public @NotNull Expr visitLam(@NotNull Expr.LamExpr expr, Context ctx) {
    var param = visitParam(expr.param(), ctx);
    var body = expr.body().accept(this, param._2);
    return new Expr.LamExpr(expr.sourcePos(), param._1, body);
  }

  @Override public @NotNull Expr visitPi(@NotNull Expr.PiExpr expr, Context ctx) {
    var param = visitParam(expr.param(), ctx);
    var last = expr.last().accept(this, param._2);
    return new Expr.PiExpr(expr.sourcePos(), expr.co(), param._1, last);
  }

  @Override public @NotNull Expr visitTelescopicSigma(@NotNull Expr.TelescopicSigmaExpr expr, Context ctx) {
    var params = resolveParams(expr.params(), ctx);
    var last = expr.last().accept(this, params._2);
    return new Expr.TelescopicSigmaExpr(expr.sourcePos(), expr.co(), params._1.collect(ImmutableSeq.factory()), last);
  }
}
