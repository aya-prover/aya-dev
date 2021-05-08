// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.resolve.visitor;

import org.aya.api.ref.LevelGenVar;
import org.aya.concrete.Expr;
import org.aya.concrete.resolve.context.Context;
import org.aya.concrete.resolve.error.GeneralizedNotAvailableError;
import org.aya.concrete.visitor.ExprFixpoint;
import org.glavo.kala.collection.SeqLike;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.tuple.Tuple2;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Resolves bindings.
 *
 * @param allowGeneralized true for signatures, false for bodies
 * @param allowedLevels    will be filled with generalized level vars if allowGeneralized,
 *                         and represents the allowed generalized level vars otherwise
 * @author re-xyr, ice1000
 * @see StmtResolver
 */
record ExprResolver(
  boolean allowGeneralized,
  @NotNull Buffer<LevelGenVar> allowedLevels
) implements ExprFixpoint<Context> {
  static final @NotNull ExprResolver NO_GENERALIZED = new ExprResolver(false, Buffer.of());

  @Override public @NotNull Expr visitUnresolved(@NotNull Expr.UnresolvedExpr expr, Context ctx) {
    var sourcePos = expr.sourcePos();
    var name = expr.name();
    var resolved = ctx.get(name);
    var refExpr = new Expr.RefExpr(sourcePos, resolved, name.justName());
    if (resolved instanceof LevelGenVar levelVar) {
      if (allowGeneralized) allowedLevels.append(levelVar);
      else if (!allowedLevels.contains(levelVar)) {
        ctx.reporter().report(new GeneralizedNotAvailableError(refExpr));
        throw new Context.ResolvingInterruptedException();
      }
    }
    return refExpr;
  }

  public @NotNull Tuple2<Expr.Param, Context> visitParam(@NotNull Expr.Param param, Context ctx) {
    var type = param.type();
    type = type == null ? null : type.accept(this, ctx);
    return Tuple2.of(
      new Expr.Param(param.sourcePos(), param.ref(), type, param.explicit()),
      ctx.bind(param.ref(), param.sourcePos())
    );
  }

  @Contract(pure = true)
  public @NotNull Tuple2<ImmutableSeq<Expr.Param>, Context>
  resolveParams(@NotNull SeqLike<Expr.Param> params, Context ctx) {
    if (params.isEmpty()) return Tuple2.of(ImmutableSeq.of(), ctx);
    var first = params.first();
    var type = first.type();
    type = type == null ? null : type.accept(this, ctx);
    var newCtx = ctx.bind(first.ref(), first.sourcePos());
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

  @Override public Expr.@NotNull Field visitField(Expr.@NotNull Field t, Context context) {
    for (var binding : t.bindings()) context = context.bind(binding.data(), binding.sourcePos());
    return ExprFixpoint.super.visitField(t, context);
  }

  @Override public @NotNull Expr visitPi(@NotNull Expr.PiExpr expr, Context ctx) {
    var param = visitParam(expr.param(), ctx);
    var last = expr.last().accept(this, param._2);
    return new Expr.PiExpr(expr.sourcePos(), expr.co(), param._1, last);
  }

  @Override public @NotNull Expr visitSigma(@NotNull Expr.SigmaExpr expr, Context ctx) {
    var params = resolveParams(expr.params(), ctx);
    return new Expr.SigmaExpr(expr.sourcePos(), expr.co(), params._1.collect(ImmutableSeq.factory()));
  }
}
