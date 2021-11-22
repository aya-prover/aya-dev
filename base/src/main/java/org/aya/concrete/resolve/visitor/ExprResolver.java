// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.resolve.visitor;

import kala.collection.SeqLike;
import kala.collection.SeqView;
import kala.collection.mutable.DynamicSeq;
import kala.collection.mutable.MutableLinkedHashMap;
import kala.collection.mutable.MutableMap;
import kala.tuple.Tuple2;
import org.aya.api.ref.DefVar;
import org.aya.api.ref.Var;
import org.aya.concrete.Expr;
import org.aya.concrete.resolve.context.Context;
import org.aya.concrete.resolve.error.GeneralizedNotAvailableError;
import org.aya.concrete.stmt.Decl;
import org.aya.concrete.stmt.Stmt;
import org.aya.concrete.visitor.ExprFixpoint;
import org.aya.generic.ref.GeneralizedVar;
import org.aya.generic.ref.PreLevelVar;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Resolves bindings.
 *
 * @param allowedLevels      will be filled with generalized level vars if allowLevels,
 *                           and represents the allowed generalized level vars otherwise
 * @param allowedGeneralizes will be filled with generalized vars if allowGeneralized,
 *                           and represents the allowed generalized level vars otherwise
 * @author re-xyr, ice1000
 * @implSpec allowedGeneralizes must be linked map
 * @see StmtResolver
 */
public record ExprResolver(
  @NotNull Options options,
  @NotNull DynamicSeq<PreLevelVar> allowedLevels,
  @NotNull MutableMap<GeneralizedVar, Expr.Param> allowedGeneralizes,
  @NotNull DynamicSeq<Stmt> reference
) implements ExprFixpoint<Context> {
  /**
   * @param allowLevels true for signatures, false for bodies
   */
  public record Options(boolean allowLevels, boolean allowGeneralized) {
  }

  public static final @NotNull Options RESTRICTIVE = new Options(false, false);
  public static final @NotNull Options LAX = new ExprResolver.Options(true, true);

  public ExprResolver(@NotNull Options options) {
    this(options, DynamicSeq.create(), MutableLinkedHashMap.of(), DynamicSeq.create());
  }

  public ExprResolver(@NotNull Options options, @NotNull ExprResolver parent) {
    this(options, parent.allowedLevels, parent.allowedGeneralizes, parent.reference);
  }

  @Override public @NotNull Expr visitUnresolved(@NotNull Expr.UnresolvedExpr expr, Context ctx) {
    var sourcePos = expr.sourcePos();
    return switch (ctx.get(expr.name())) {
      case PreLevelVar levelVar -> {
        if (options.allowLevels) allowedLevels.append(levelVar);
        else if (!allowedLevels.contains(levelVar)) generalizedUnavailable(ctx, sourcePos, levelVar);
        yield new Expr.RefExpr(sourcePos, levelVar);
      }
      case GeneralizedVar generalized -> {
        if (options.allowGeneralized) {
          // Ordered set semantics. Do not expect too many generalized vars.
          if (!allowedGeneralizes.containsKey(generalized)) {
            var owner = generalized.owner;
            assert owner != null : "Sanity check";
            allowedGeneralizes.put(generalized, owner.toExpr(false, generalized.toLocal()));
            reference.append(owner);
          }
        } else if (!allowedGeneralizes.containsKey(generalized))
          generalizedUnavailable(ctx, sourcePos, generalized);
        yield new Expr.RefExpr(sourcePos, allowedGeneralizes.get(generalized).ref());
      }
      case DefVar<?, ?> ref -> {
        switch (ref.concrete) {
          case Decl decl -> reference.append(decl);
          case Decl.DataCtor ctor -> reference.append(ctor.dataRef.concrete);
          case Decl.StructField field -> reference.append(field.structRef.concrete);
          default -> throw new IllegalStateException("unreachable");
        }
        yield new Expr.RefExpr(sourcePos, ref);
      }
      case Var var -> new Expr.RefExpr(sourcePos, var);
    };
  }

  private void generalizedUnavailable(Context ctx, SourcePos refExpr, Var var) {
    ctx.reporter().report(new GeneralizedNotAvailableError(refExpr, var));
    throw new Context.ResolvingInterruptedException();
  }

  public @NotNull Tuple2<Expr.Param, Context> visitParam(@NotNull Expr.Param param, Context ctx) {
    var type = param.type();
    type = type == null ? null : type.accept(this, ctx);
    return Tuple2.of(new Expr.Param(param, type), ctx.bind(param.ref(), param.sourcePos()));
  }

  @Contract(pure = true)
  public @NotNull Tuple2<SeqView<Expr.Param>, Context>
  resolveParams(@NotNull SeqLike<Expr.Param> params, Context ctx) {
    if (params.isEmpty()) return Tuple2.of(SeqView.empty(), ctx);
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
    return new Expr.SigmaExpr(expr.sourcePos(), expr.co(), params._1.toImmutableSeq());
  }

  @Override public @NotNull Expr visitHole(@NotNull Expr.HoleExpr expr, Context context) {
    expr.accessibleLocal().set(context.collect(DynamicSeq.create()).toImmutableSeq());
    return ExprFixpoint.super.visitHole(expr, context);
  }
}
