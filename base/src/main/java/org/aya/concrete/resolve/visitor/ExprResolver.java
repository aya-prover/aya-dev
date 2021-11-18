// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.resolve.visitor;

import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.DynamicSeq;
import kala.tuple.Tuple2;
import org.aya.api.ref.DefVar;
import org.aya.api.ref.PreLevelVar;
import org.aya.concrete.Expr;
import org.aya.concrete.resolve.context.Context;
import org.aya.concrete.resolve.error.GeneralizedNotAvailableError;
import org.aya.concrete.stmt.Decl;
import org.aya.concrete.stmt.Stmt;
import org.aya.concrete.visitor.ExprFixpoint;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Resolves bindings.
 *
 * @param allowedLevels will be filled with generalized level vars if allowGeneralized,
 *                      and represents the allowed generalized level vars otherwise
 * @author re-xyr, ice1000
 * @see StmtResolver
 */
public record ExprResolver(
  @NotNull Options options,
  @NotNull DynamicSeq<PreLevelVar> allowedLevels,
  @NotNull DynamicSeq<Stmt> reference
) implements ExprFixpoint<Context> {
  /**
   * @param allowGeneralized true for signatures, false for bodies
   */
  public record Options(boolean allowGeneralized) {
  }

  public static final @NotNull Options RESTRICTIVE = new Options(false);

  public ExprResolver(@NotNull Options options) {
    this(options, DynamicSeq.create(), DynamicSeq.create());
  }

  public ExprResolver(@NotNull Options options, @NotNull ExprResolver parent) {
    this(options, parent.allowedLevels, parent.reference);
  }

  @Override public @NotNull Expr visitUnresolved(@NotNull Expr.UnresolvedExpr expr, Context ctx) {
    var sourcePos = expr.sourcePos();
    var name = expr.name();
    var resolved = ctx.get(name);
    var refExpr = new Expr.RefExpr(sourcePos, resolved);
    switch (resolved) {
      case PreLevelVar levelVar -> {
        if (options.allowGeneralized) allowedLevels.append(levelVar);
        else if (!allowedLevels.contains(levelVar)) {
          ctx.reporter().report(new GeneralizedNotAvailableError(refExpr));
          throw new Context.ResolvingInterruptedException();
        }
      }
      case DefVar<?, ?> ref -> {
        switch (ref.concrete) {
          case Decl decl -> reference.append(decl);
          case Decl.DataCtor ctor -> reference.append(ctor.dataRef.concrete);
          case Decl.StructField field -> reference.append(field.structRef.concrete);
          default -> throw new IllegalStateException("unreachable");
        }
      }
      default -> {
      }
    }
    return refExpr;
  }

  public @NotNull Tuple2<Expr.Param, Context> visitParam(@NotNull Expr.Param param, Context ctx) {
    var type = param.type();
    type = type == null ? null : type.accept(this, ctx);
    return Tuple2.of(new Expr.Param(param, type), ctx.bind(param.ref(), param.sourcePos()));
  }

  @Contract(pure = true)
  public @NotNull Tuple2<ImmutableSeq<Expr.Param>, Context>
  resolveParams(@NotNull SeqLike<Expr.Param> params, Context ctx) {
    if (params.isEmpty()) return Tuple2.of(ImmutableSeq.empty(), ctx);
    var first = params.first();
    var type = first.type();
    type = type == null ? null : type.accept(this, ctx);
    var newCtx = ctx.bind(first.ref(), first.sourcePos());
    var result = resolveParams(params.view().drop(1), newCtx);
    return Tuple2.of(result._1.prepended(new Expr.Param(first, type)), result._2);
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
    return new Expr.SigmaExpr(expr.sourcePos(), expr.co(), params._1);
  }

  @Override public @NotNull Expr visitHole(@NotNull Expr.HoleExpr expr, Context context) {
    expr.accessibleLocal().set(context.collect(DynamicSeq.create()).toImmutableSeq());
    return ExprFixpoint.super.visitHole(expr, context);
  }
}
