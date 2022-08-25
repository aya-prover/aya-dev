// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.visitor;

import kala.collection.SeqLike;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableLinkedHashMap;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import kala.collection.mutable.MutableStack;
import kala.tuple.Tuple2;
import org.aya.concrete.Expr;
import org.aya.generic.ref.GeneralizedVar;
import org.aya.generic.util.InternalException;
import org.aya.ref.DefVar;
import org.aya.ref.LocalVar;
import org.aya.ref.Var;
import org.aya.resolve.context.Context;
import org.aya.resolve.error.GeneralizedNotAvailableError;
import org.aya.tyck.error.FieldProblem;
import org.aya.tyck.order.TyckOrder;
import org.aya.tyck.order.TyckUnit;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Resolves bindings.
 *
 * @param allowedGeneralizes will be filled with generalized vars if allowGeneralized,
 *                           and represents the allowed generalized level vars otherwise
 * @author re-xyr, ice1000
 * @implSpec allowedGeneralizes must be linked map
 * @see StmtResolver
 */
public record ExprResolver(
  @NotNull Options options,
  @NotNull MutableMap<GeneralizedVar, Expr.Param> allowedGeneralizes,
  @NotNull MutableList<TyckOrder> reference,
  @NotNull MutableStack<Where> where,
  @Nullable Consumer<TyckUnit> parentAdd
) {
  public @NotNull Expr resolve(@NotNull Expr expr, @NotNull Context ctx) {
    return switch (expr) {
      case Expr.LiftExpr lift -> {
        var mapped = resolve(lift.expr(), ctx);
        if (mapped == lift.expr()) yield expr;
        yield new Expr.LiftExpr(expr.sourcePos(), mapped, lift.lift());
      }
      case Expr.AppExpr app -> {
        var function = resolve(app.function(), ctx);
        var argument = app.argument();
        var argExpr = resolve(argument.expr(), ctx);
        if (function == app.function() && argExpr == argument.expr()) yield app;
        var newArg = new Expr.NamedArg(argument.explicit(), argument.name(), argExpr);
        yield new Expr.AppExpr(app.sourcePos(), function, newArg);
      }
      case Expr.TupExpr tup -> {
        var items = tup.items().map(item -> resolve(item, ctx));
        if (items.sameElements(tup.items(), true)) yield expr;
        yield new Expr.TupExpr(expr.sourcePos(), items);
      }
      case Expr.NewExpr newExpr -> {
        var struct = resolve(newExpr.struct(), ctx);
        var fields = newExpr.fields().map(t -> resolveField(t, ctx));
        if (newExpr.struct() == struct && fields.sameElements(newExpr.fields(), true)) yield newExpr;
        yield new Expr.NewExpr(newExpr.sourcePos(), struct, fields);
      }
      case Expr.BinOpSeq binOpSeq -> new Expr.BinOpSeq(binOpSeq.sourcePos(),
        binOpSeq.seq().map(e -> new Expr.NamedArg(e.explicit(), e.name(), resolve(e.expr(), ctx))));
      case Expr.ProjExpr proj -> {
        var tup = resolve(proj.tup(), ctx);
        if (proj.ix().isLeft())
          yield new Expr.ProjExpr(proj.sourcePos(), tup, proj.ix(), proj.resolvedIx(), proj.theCore());
        var projName = proj.ix().getRightValue();
        var resolvedIx = ctx.getMaybe(projName);
        if (resolvedIx == null) ctx.reportAndThrow(new FieldProblem.UnknownField(proj, projName.join()));
        yield new Expr.ProjExpr(proj.sourcePos(), tup, proj.ix(), resolvedIx, proj.theCore());
      }
      case Expr.LamExpr lam -> {
        var param = resolveParam(lam.param(), ctx);
        var body = resolve(lam.body(), param._2);
        yield new Expr.LamExpr(lam.sourcePos(), param._1, body);
      }
      case Expr.SigmaExpr sigma -> {
        var params = resolveParams(sigma.params(), ctx);
        yield new Expr.SigmaExpr(sigma.sourcePos(), sigma.co(), params._1.toImmutableSeq());
      }
      case Expr.PiExpr pi -> {
        var param = resolveParam(pi.param(), ctx);
        var last = resolve(pi.last(), param._2);
        yield new Expr.PiExpr(pi.sourcePos(), pi.co(), param._1, last);
      }
      case Expr.HoleExpr hole -> {
        hole.accessibleLocal().set(ctx.collect(MutableList.create()).toImmutableSeq());
        var h = hole.filling() != null ? resolve(hole.filling(), ctx) : null;
        if (h == hole.filling()) yield hole;
        yield new Expr.HoleExpr(hole.sourcePos(), hole.explicit(), h, hole.accessibleLocal());
      }
      case Expr.PartEl el -> {
        var clauses = el.clauses().map(c -> c.rename(e -> resolve(e, ctx)));
        if (clauses.sameElements(el.clauses(), true)) yield el;
        yield new Expr.PartEl(el.sourcePos(), clauses);
      }
      case Expr.PartTy ty -> {
        var type = resolve(ty.type(), ctx);
        var restr = ty.restr().fmap(r -> resolve(r, ctx));
        if (type == ty.type() && restr == ty.restr()) yield ty;
        yield new Expr.PartTy(ty.sourcePos(), type, restr);
      }
      case Expr.Path path -> {
        var newCtx = resolveCubeParams(path.cube().params(), ctx);
        var cube = path.cube().map(e -> resolve(e, newCtx));
        if (cube == path.cube()) yield path;
        yield new Expr.Path(path.sourcePos(), cube);
      }
      case Expr.UnresolvedExpr unresolved -> {
        var sourcePos = unresolved.sourcePos();
        yield switch (ctx.get(unresolved.name())) {
          case GeneralizedVar generalized -> {
            if (options.allowGeneralized) {
              // Ordered set semantics. Do not expect too many generalized vars.
              if (!allowedGeneralizes.containsKey(generalized)) {
                var owner = generalized.owner;
                assert owner != null : "Sanity check";
                allowedGeneralizes.put(generalized, owner.toExpr(false, generalized.toLocal()));
                addReference(owner);
              }
            } else if (!allowedGeneralizes.containsKey(generalized))
              generalizedUnavailable(ctx, sourcePos, generalized);
            yield new Expr.RefExpr(sourcePos, allowedGeneralizes.get(generalized).ref());
          }
          case DefVar<?, ?> ref -> {
            switch (ref.concrete) {
              case null -> {
                // RefExpr is referring to a serialized core which is already tycked.
                // Collecting tyck order for tycked terms is unnecessary, just skip.
                assert ref.core != null; // ensure it is tycked
              }
              case TyckUnit unit -> addReference(unit);
            }
            yield new Expr.RefExpr(sourcePos, ref);
          }
          case Var var -> new Expr.RefExpr(sourcePos, var);
        };
      }
      default -> expr;
    };
  }

  enum Where {
    Head, Body
  }

  public void enterHead() {
    where.push(Where.Head);
    reference.clear();
  }

  public void enterBody() {
    where.push(Where.Body);
    reference.clear();
  }

  private void addReference(@NotNull TyckUnit unit) {
    if (parentAdd != null) parentAdd.accept(unit);
    if (where.isEmpty()) throw new InternalException("where am I?");
    if (where.peek() == Where.Head) {
      reference.append(new TyckOrder.Head(unit));
      reference.append(new TyckOrder.Body(unit));
    } else {
      reference.append(new TyckOrder.Body(unit));
    }
  }

  /**
   * @param allowLevels true for signatures, false for bodies
   */
  public record Options(boolean allowLevels, boolean allowGeneralized) {
  }

  public static final @NotNull Options RESTRICTIVE = new Options(false, false);
  public static final @NotNull Options LAX = new ExprResolver.Options(true, true);

  public ExprResolver(@NotNull Options options) {
    this(options, MutableLinkedHashMap.of(), MutableList.create(), MutableStack.create(), null);
  }

  public @NotNull ExprResolver member(@NotNull TyckUnit decl) {
    return new ExprResolver(RESTRICTIVE, allowedGeneralizes, MutableList.of(new TyckOrder.Head(decl)), MutableStack.create(),
      this::addReference);
  }

  public @NotNull ExprResolver body() {
    return new ExprResolver(RESTRICTIVE, allowedGeneralizes, reference, MutableStack.create(),
      this::addReference);
  }

  private void generalizedUnavailable(Context ctx, SourcePos refExpr, Var var) {
    ctx.reporter().report(new GeneralizedNotAvailableError(refExpr, var));
    throw new Context.ResolvingInterruptedException();
  }

  private @NotNull Tuple2<Expr.Param, Context> resolveParam(@NotNull Expr.Param param, Context ctx) {
    var type = resolve(param.type(), ctx);
    return Tuple2.of(new Expr.Param(param, type), ctx.bind(param.ref(), param.sourcePos()));
  }

  private @NotNull Context resolveCubeParams(@NotNull ImmutableSeq<LocalVar> params, Context ctx) {
    return params.foldLeft(ctx, (c, x) -> c.bind(x, x.definition()));
  }

  @Contract(pure = true)
  public @NotNull Tuple2<SeqView<Expr.Param>, Context>
  resolveParams(@NotNull SeqLike<Expr.Param> params, Context ctx) {
    if (params.isEmpty()) return Tuple2.of(SeqView.empty(), ctx);
    var first = params.first();
    var type = resolve(first.type(), ctx);
    var newCtx = ctx.bind(first.ref(), first.sourcePos());
    var result = resolveParams(params.view().drop(1), newCtx);
    return Tuple2.of(result._1.prepended(new Expr.Param(first, type)), result._2);
  }

  private Expr.@NotNull Field resolveField(Expr.@NotNull Field t, Context context) {
    for (var binding : t.bindings()) context = context.bind(binding.data(), binding.sourcePos());
    var accept = resolve(t.body(), context);
    if (accept == t.body()) return t;
    return new Expr.Field(t.name(), t.bindings(), accept, t.resolvedField());
  }
}
