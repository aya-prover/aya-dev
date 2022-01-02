// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.value;

import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.DynamicSeq;
import kala.collection.mutable.MutableMap;
import org.aya.api.ref.LocalVar;
import org.aya.concrete.Expr;
import org.aya.core.Meta;
import org.aya.core.term.CallTerm;
import org.aya.core.term.FormTerm;
import org.aya.core.term.IntroTerm;
import org.aya.core.term.Term;
import org.aya.generic.Constants;
import org.aya.generic.Environment;
import org.jetbrains.annotations.NotNull;

public class Elaborator {
  private final @NotNull Evaluator evaluator;
  private final @NotNull Quoter quoter;
  private final @NotNull Unifier unifier;

  public Elaborator(@NotNull MutableMap<Meta, Value> metaCtx) {
    evaluator = new Evaluator(metaCtx);
    quoter = new Quoter();
    unifier = new Unifier(metaCtx);
  }

  private Value eval(Ctx ctx, Term term) {
    return evaluator.eval(ctx.env, term);
  }

  private Term quote(Value value) {
    return quoter.quote(value);
  }

  private Boolean unify(Value left, Value right) {
    return unifier.unify(left, right);
  }

  public Term check(Ctx ctx, Expr expr, Value type) {
    final var ty = type.force();
    return switch (expr) {
      case Expr.SigmaExpr sigma && sigma.params().isEmpty() && ty instanceof FormValue.Univ -> new FormTerm.Sigma(ImmutableSeq.empty());
      case Expr.SigmaExpr sigma && ty instanceof FormValue.Univ univ -> {
        var param = sigma.params().first();
        var paramTy = check(ctx, param.type(), univ);
        var tailExpr = new Expr.SigmaExpr(sigma.sourcePos(), sigma.co(), sigma.params().drop(1));
        var tailTerm = (FormTerm.Sigma) check(ctx.bind(param.ref(), paramTy), tailExpr, univ);
        yield new FormTerm.Sigma(tailTerm.params().prepended(new Term.Param(param, paramTy)));
      }
      case Expr.PiExpr pi && ty instanceof FormValue.Univ univ -> {
        var param = pi.param();
        var paramType = check(ctx, param.type(), univ);
        var body = check(ctx.bind(param.ref(), paramType), pi.last(), univ);
        yield new FormTerm.Pi(new Term.Param(param, paramType), body);
      }
      case Expr.TupExpr tuple && tuple.items().isEmpty() && ty instanceof FormValue.Unit -> new IntroTerm.Tuple(ImmutableSeq.empty());
      case Expr.TupExpr tuple && ty instanceof FormValue.Sigma sigma -> {
        var head = check(ctx, tuple.items().first(), sigma.param().type());
        var tailType = sigma.func().apply(eval(ctx, head));
        var tailExpr = new Expr.TupExpr(tuple.sourcePos(), tuple.items().drop(1));
        var tailTerm = (IntroTerm.Tuple) check(ctx, tailExpr, tailType);
        yield new IntroTerm.Tuple(tailTerm.items().prepended(head));
      }
      case Expr.LamExpr lambda && ty instanceof FormValue.Pi pi && lambda.param().explicit() == pi.param().explicit() -> {
        var param = lambda.param();
        var paramTy = quote(pi.param().type());
        var annType = param.type();
        var annTy = check(ctx, annType, FormValue.Univ.fresh(annType.sourcePos()));
        assert unify(eval(ctx, annTy), pi.param().type());
        var freshVar = new RefValue.Neu(new LocalVar(param.ref().name()));
        var body = check(ctx.bind(param.ref(), freshVar, paramTy), lambda.body(), pi.func().apply(freshVar));
        yield new IntroTerm.Lambda(new Term.Param(param, paramTy), body);
      }
      case Expr.LamExpr lambda && ty instanceof FormValue.Pi pi && !pi.param().explicit() -> {
        var param = lambda.param();
        var freshVar = new RefValue.Neu(new LocalVar(param.ref().name()));
        var paramTy = quote(pi.param().type());
        var body = check(ctx.bind(param.ref(), freshVar, paramTy), lambda, pi.func().apply(freshVar));
        yield new IntroTerm.Lambda(new Term.Param(pi.param().ref(), paramTy, false), body);
      }
      case Expr.HoleExpr hole -> {
        var tele = DynamicSeq.<Term.Param>create();
        ctx.tele.mapTo(tele, (k, v) -> new Term.Param(k, v, false));
        var ctxTele = tele.toImmutableSeq();
        // TODO[wsx]:
        //  Verify we need the telescope of meta.
        //  This is derived from the type of the hole,
        //  which I assume should be normalized before constructing meta.
        var meta = Meta.from(ctxTele, Constants.randomName(hole), quote(type), hole.sourcePos());
        var holeTerm = new CallTerm.Hole(meta, ctxTele.map(Term.Param::toArg), meta.telescope.map(Term.Param::toArg));
        yield IntroTerm.Lambda.make(meta.telescope, holeTerm);
      }
      case default -> {
        var result = infer(ctx, expr);
        assert unify(result.type, type);
        yield result.term;
      }
    };
  }

  private InferRes infer(Ctx ctx, Expr expr) {
    return switch (expr) {
      case Expr.SigmaExpr sigma -> {
        var univ = FormValue.Univ.fresh(sigma.sourcePos());
        var sig = check(ctx, sigma, univ);
        yield new InferRes(sig, univ);
      }
      case Expr.PiExpr pi -> {
        var univ = FormValue.Univ.fresh(pi.sourcePos());
        var p = check(ctx, pi, univ);
        yield new InferRes(p, univ);
      }
      case default -> null;
    };
  }

  public record Ctx(Environment env, ImmutableMap<LocalVar, Term> tele) {
    public Ctx bind(LocalVar var, Term type) {
      return new Ctx(env.added(var, new RefValue.Neu(new LocalVar(var.name()))), tele.updated(var, type));
    }

    public Ctx bind(LocalVar var, Value value, Term type) {
      return new Ctx(env.added(var, value), tele.updated(var, type));
    }
  }

  private record InferRes(Term term, Value type) {}
}
