// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.tyck;

import org.glavo.kala.Tuple;
import org.glavo.kala.Tuple3;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.collection.mutable.MutableHashMap;
import org.glavo.kala.collection.mutable.MutableMap;
import org.glavo.kala.ref.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.error.Reporter;
import org.mzi.api.ref.Var;
import org.mzi.api.util.NormalizeMode;
import org.mzi.concrete.Expr;
import org.mzi.core.Param;
import org.mzi.core.term.*;
import org.mzi.core.visitor.Substituter;
import org.mzi.generic.Arg;
import org.mzi.pretty.doc.Doc;
import org.mzi.ref.LocalVar;
import org.mzi.tyck.error.BadTypeError;
import org.mzi.tyck.sort.Sort;
import org.mzi.tyck.unify.NaiveDefEq;
import org.mzi.tyck.unify.Rule;
import org.mzi.util.Ordering;

import java.util.HashMap;

public class ExprTycker implements Expr.BaseVisitor<Term, ExprTycker.Result> {
  public final @NotNull MetaContext metaContext;
  public final @NotNull MutableMap<Var, Term> localCtx;

  public ExprTycker(@NotNull Reporter reporter) {
    this(new MetaContext(reporter));
  }

  public ExprTycker(@NotNull MetaContext metaContext) {
    this(metaContext, new MutableHashMap<>());
  }

  public ExprTycker(@NotNull Reporter reporter, @NotNull MutableMap<Var, Term> localCtx) {
    this(new MetaContext(reporter), localCtx);
  }

  public ExprTycker(@NotNull MetaContext metaContext, @NotNull MutableMap<Var, Term> localCtx) {
    this.localCtx = localCtx;
    this.metaContext = metaContext;
  }

  public @NotNull Result finalize(@NotNull Result result) {
    return new Result(
      result.wellTyped.strip(metaContext),
      result.type.strip(metaContext)
    );
  }

  public @NotNull Result checkExpr(@NotNull Expr expr, @Nullable Term type) {
    return finalize(expr.accept(this, type));
  }

  @Rule.Check(partialSynth = true)
  @Override
  public Result visitLam(Expr.@NotNull LamExpr expr, @Nullable Term term) {
    if (term == null) {
      var domain = new LocalVar("_");
      var codomain = new LocalVar("_");
      term = new PiTerm(false, Param.mock(domain, expr.param().explicit()), new AppTerm.HoleApp(codomain));
    }
    if (!(term.normalize(NormalizeMode.WHNF) instanceof PiTerm dt && !dt.co())) {
      return wantButNo(expr, term, "pi type");
    }
    var tyRef = new Ref<>(term);
    var var = expr.param().var();
    var param = expr.param();
    if (tyRef.value instanceof PiTerm pi && !pi.co()) {
      var type = pi.param().type();
      var lamParam = param.type();
      if (lamParam != null) {
        var result = lamParam.accept(this, UnivTerm.OMEGA);
        var comparison = new NaiveDefEq(Ordering.Lt, metaContext).compare(result.wellTyped, type, UnivTerm.OMEGA);
        if (!comparison) {
          // TODO[ice]: expected type mismatch lambda type annotation
          throw new TyckerException();
        } else type = result.wellTyped;
      }
      type = type.subst(pi.param().ref(), new RefTerm(var));
      var resultParam = new Param(var, type, param.explicit());
      localCtx.put(var, type);
      tyRef.value = pi.body();
      var rec = expr.body().accept(this, tyRef.value);
      return new Result(new LamTerm(resultParam, rec.wellTyped), dt);
    } else return wantButNo(expr, tyRef.value, "pi type");
  }

  private <T> T wantButNo(@NotNull Expr expr, Term term, String expectedText) {
    metaContext.report(new BadTypeError(expr, Doc.plain(expectedText), term));
    throw new TyckerException();
  }

  @Rule.Synth
  @Override public Result visitUniv(Expr.@NotNull UnivExpr expr, @Nullable Term term) {
    if (term == null) return new Result(new UnivTerm(Sort.OMEGA), new UnivTerm(Sort.OMEGA));
    if (term.normalize(NormalizeMode.WHNF) instanceof UnivTerm univ) {
      // TODO[level]
      return new Result(new UnivTerm(Sort.OMEGA), univ);
    }
    return wantButNo(expr, term, "universe term");
  }

  @Rule.Synth
  @Override public Result visitRef(Expr.@NotNull RefExpr expr, @Nullable Term term) {
    var ty = localCtx.get(expr.resolvedVar());
    if (ty == null) throw new IllegalStateException("Unresolved var `" + expr.resolvedVar().name() + "` tycked.");
    if (term == null) return new Result(new RefTerm(expr.resolvedVar()), ty);
    unify(term, ty);
    return new Result(new RefTerm(expr.resolvedVar()), ty);
  }

  private void unify(Term upper, Term lower) {
    var unification = new NaiveDefEq(Ordering.Lt, metaContext).compare(lower, upper, UnivTerm.OMEGA);
    if (!unification) {
      // TODO[ice]: expected type mismatch synthesized type
      throw new TyckerException();
    }
  }

  @Rule.Synth
  @Override public Result visitPi(Expr.@NotNull PiExpr expr, @Nullable Term term) {
    final var against = term != null ? term : new UnivTerm(Sort.OMEGA);
    var var = expr.param().var();
    var param = expr.param();
    final var type = param.type();
    if (type == null) {
      // TODO[ice]: report error or generate meta?
      //  I guess probably report error for now.
      throw new TyckerException();
    }
    var result = type.accept(this, against);
    var resultParam = new Param(var, result.wellTyped, param.explicit());
    var last = expr.last().accept(this, against);
    return new Result(new PiTerm(expr.co(), resultParam, last.wellTyped), against);
  }

  @Rule.Synth
  @Override public Result visitTelescopicSigma(Expr.@NotNull TelescopicSigmaExpr expr, @Nullable Term term) {
    final var against = term != null ? term : new UnivTerm(Sort.OMEGA);
    var resultTele = Buffer.<Tuple3<Var, Boolean, Term>>of();
    expr.paramsStream().forEach(tuple -> {
      final var type = tuple._2.type();
      if (type == null) {
        // TODO[ice]: report error or generate meta?
        //  I guess probably report error for now.
        throw new TyckerException();
      }
      var result = type.accept(this, against);
      resultTele.append(Tuple.of(tuple._1, tuple._2.explicit(), result.wellTyped));
    });
    var last = expr.last().accept(this, against);
    return new Result(new SigmaTerm(expr.co(), Param.fromBuffer(resultTele), last.wellTyped), against);
  }

  @Rule.Synth
  @Override public Result visitProj(Expr.@NotNull ProjExpr expr, @Nullable Term term) {
    var tupleRes = expr.tup().accept(this, null);
    if (!(tupleRes.type instanceof SigmaTerm dt && !dt.co()))
      return wantButNo(expr.tup(), tupleRes.type, "sigma type");
    var telescope = dt.params();
    var index = expr.ix() - 1;
    if (index < 0) {
      // TODO[ice]: too small index
      throw new TyckerException();
    } else if (index > telescope.size()) {
      // TODO[ice]: too large index
      throw new TyckerException();
    }
    var type = index == telescope.size() ? dt.body() : telescope.get(index).type();
    // instantiate the type
    var fieldsBefore = telescope.take(index);
    var subst = new Substituter.TermSubst(new HashMap<>());
    fieldsBefore.forEachIndexed((i, param) ->
      subst.add(param.ref(), new ProjTerm(tupleRes.wellTyped, i + 1)));
    type = type.subst(subst);
    unify(term, type);
    return new Result(new ProjTerm(tupleRes.wellTyped, expr.ix()), type);
  }

  @Override public Result visitHole(Expr.@NotNull HoleExpr expr, Term term) {
    // TODO[ice]: deal with unit type
    var name = expr.name();
    if (name == null) name = "_";
    if (term == null) term = new AppTerm.HoleApp(new LocalVar(name + "_ty"));
    return new Result(new AppTerm.HoleApp(new LocalVar(name)), term);
  }

  @Rule.Synth
  @Override public Result visitApp(Expr.@NotNull AppExpr expr, @Nullable Term term) {
    var f = expr.function().accept(this, null);
    var resultTerm = f.wellTyped;
    if (!(f.type instanceof PiTerm piTerm)) return wantButNo(expr, f.type, "pi type");
    var pi = piTerm;
    for (var iter = expr.argument().iterator(); iter.hasNext(); ) {
      var arg = iter.next();
      var param = pi.param();
      var paramLicit = param.explicit();
      var argLicit = arg.explicit();
      if (paramLicit == argLicit) {
        var elabArg = arg.term().accept(this, param.type());
        resultTerm = AppTerm.make(resultTerm, new Arg<>(elabArg.wellTyped, argLicit));
      } else if (argLicit) {
        // that implies paramLicit == false
        var holeApp = new AppTerm.HoleApp(new LocalVar("_"));
        // TODO: maybe we should create a concrete hole and check it against the type
        //  in case we can synthesize this term via its type only
        resultTerm = AppTerm.make(resultTerm, new Arg<>(holeApp, false));
      } else {
        // TODO[ice]: no implicit argument expected, but inserted.
        throw new TyckerException();
      }
      // so, in the end, the pi term is not updated, its body would be the eliminated type
      if (iter.hasNext()) {
        if (pi.body() instanceof PiTerm newPi) pi = newPi;
        else wantButNo(expr, pi.body(), "pi type");
      }
    }
    return new Result(resultTerm, pi.body());
  }

  @Rule.Check(partialSynth = true)
  @Override public Result visitTup(Expr.@NotNull TupExpr expr, @Nullable Term term) {
    var items = Buffer.<Term>of();
    final var resultLast = new Ref<Term>();
    final Buffer<@NotNull Param> resultTele;
    if (term == null) {
      var typesTele = Buffer.<@NotNull Param>of();
      // TODO[ice]: forbid one-variable tuple maybe?
      expr.items()
        .map(item -> item.accept(this, null))
        .forEach(result -> {
          items.append(result.wellTyped);
          if (resultLast.value == null) resultLast.value = result.type;
          else typesTele.append(new Param(new LocalVar("_"), result.type, true));
        });
      items.reverse();
      resultTele = typesTele;
    } else if (!(term instanceof SigmaTerm dt && !dt.co())) {
      return wantButNo(expr, term, "sigma type");
    } else {
      var againstTele = dt.params();
      var last = dt.body();
      var buffer = Buffer.<@NotNull Param>of();
      for (var iterator = expr.items().iterator(); iterator.hasNext(); ) {
        var item = iterator.next();
        if (againstTele == null) {
          if (iterator.hasNext()) {
            // TODO[ice]: not enough sigma elements
            throw new TyckerException();
          } else {
            var result = item.accept(this, last);
            items.append(result.wellTyped);
            resultLast.value = result.type;
          }
        } else {
          var result = item.accept(this, againstTele.first().type());
          items.append(result.wellTyped);
          var ref = againstTele.first().ref();
          buffer.append(new Param(ref, result.type, againstTele.first().explicit()));
          againstTele = againstTele.drop(1);
          if (!againstTele.isEmpty()) {
            final var subst = new Substituter.TermSubst(ref, result.wellTyped);
            againstTele = againstTele.map(param -> param.subst(subst));
            last = last.subst(subst);
          }
        }
      }
      resultTele = buffer;
    }
    var resultType = new SigmaTerm(false, resultTele.toImmutableSeq(), resultLast.value);
    return new Result(new TupTerm(items.toImmutableSeq()), resultType);
  }

  @Override
  public Result catchUnhandled(@NotNull Expr expr, Term term) {
    throw new UnsupportedOperationException(expr.toDoc().renderWithPageWidth(80)); // TODO[kiva]: get terminal width
  }

  public static class TyckerException extends RuntimeException {
  }

  public static record Result(
    @NotNull Term wellTyped,
    @NotNull Term type
  ) {
  }
}
