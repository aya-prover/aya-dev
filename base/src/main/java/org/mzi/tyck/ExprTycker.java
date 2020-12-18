// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.tyck;

import asia.kala.Tuple;
import asia.kala.Tuple3;
import asia.kala.collection.mutable.Buffer;
import asia.kala.collection.mutable.MutableHashMap;
import asia.kala.collection.mutable.MutableMap;
import asia.kala.ref.Ref;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.error.Reporter;
import org.mzi.api.ref.Var;
import org.mzi.api.util.DTKind;
import org.mzi.api.util.NormalizeMode;
import org.mzi.concrete.Expr;
import org.mzi.core.Tele;
import org.mzi.core.term.*;
import org.mzi.pretty.doc.Doc;
import org.mzi.ref.LocalVar;
import org.mzi.tyck.error.BadTypeError;
import org.mzi.tyck.sort.LevelEqn;
import org.mzi.tyck.sort.Sort;
import org.mzi.tyck.unify.NaiveDefEq;
import org.mzi.tyck.unify.Rule;
import org.mzi.util.Ordering;

public class ExprTycker implements Expr.BaseVisitor<Term, ExprTycker.Result> {
  public final @NotNull Reporter reporter;
  public final @NotNull MutableMap<Var, Term> localCtx;
  public final @NotNull LevelEqn.Set levelEqns;

  public ExprTycker(@NotNull Reporter reporter) {
    this.reporter = reporter;
    localCtx = new MutableHashMap<>();
    levelEqns = new LevelEqn.Set(reporter, Buffer.of(), Buffer.of());
  }

  @Rule.Check(partialSynth = true)
  @Override
  public Result visitLam(Expr.@NotNull LamExpr expr, Term term) {
    if (term == null) {
      var domain = new LocalVar("_");
      var codomain = new LocalVar("_");
      term = new DT(DTKind.Pi, Tele.mock(domain, expr.params().first().explicit()), new AppTerm.HoleApp(codomain));
    }
    if (!(term.normalize(NormalizeMode.WHNF) instanceof DT dt && dt.kind().isPi)) {
      return wantButNo(expr, term, "pi type");
    }
    var tyRef = new Ref<>(term);
    var resultTele = Buffer.<Tuple3<Var, Boolean, Term>>of();
    expr.paramsStream().forEach(tuple -> {
      if (tyRef.value instanceof DT pi && pi.kind().isPi) {
        var type = pi.telescope().type();
        var lamParam = tuple._2.type();
        // FIXME[xyr]: https://github.com/ice1000/mzi/issues/92
        //noinspection ConstantConditions
        if (lamParam != null) {
          var result = lamParam.accept(this, UnivTerm.OMEGA);
          var comparison = new NaiveDefEq(Ordering.Lt, levelEqns).compare(result.wellTyped, type, UnivTerm.OMEGA);
          if (!comparison) {
            // TODO[ice]: expected type mismatch lambda type annotation
            throw new TyckerException();
          } else type = result.wellTyped;
        }
        type = type.subst(pi.telescope().ref(), new RefTerm(tuple._1));
        resultTele.append(Tuple.of(tuple._1, tuple._2.explicit(), type));
        localCtx.put(tuple._1, type);
        tyRef.value = pi.dropTeleDT(1);
      } else wantButNo(expr, tyRef.value, "pi type");
    });
    assert tyRef.value != null;
    var rec = expr.body().accept(this, tyRef.value);
    return new Result(new LamTerm(Tele.fromBuffer(resultTele), rec.wellTyped), dt);
  }

  private <T> T wantButNo(@NotNull Expr expr, Term term, String expectedText) {
    reporter.report(new BadTypeError(expr, Doc.plain(expectedText), term));
    throw new TyckerException();
  }

  @Rule.Synth
  @Override public Result visitUniv(Expr.@NotNull UnivExpr expr, Term term) {
    if (term == null) return new Result(new UnivTerm(Sort.OMEGA), new UnivTerm(Sort.OMEGA));
    if (term.normalize(NormalizeMode.WHNF) instanceof UnivTerm univ) {
      // TODO[level]
      return new Result(new UnivTerm(Sort.OMEGA), univ);
    }
    return wantButNo(expr, term, "universe term");
  }

  @Rule.Synth
  @Override public Result visitRef(Expr.@NotNull RefExpr expr, Term term) {
    var ty = localCtx.get(expr.resolvedVar());
    if (ty == null) throw new IllegalStateException("Unresolved var `" + expr.resolvedVar().name() + "` tycked.");
    if (term == null) return new Result(new RefTerm(expr.resolvedVar()), ty);
    unify(term, ty);
    return new Result(new RefTerm(expr.resolvedVar()), ty);
  }

  private void unify(Term upper, Term lower) {
    var unification = new NaiveDefEq(Ordering.Lt, levelEqns).compare(lower, upper, UnivTerm.OMEGA);
    if (!unification) {
      // TODO[ice]: expected type mismatch synthesized type
      throw new TyckerException();
    }
  }

  @Rule.Synth
  @Override public Result visitDT(Expr.@NotNull DTExpr expr, Term term) {
    final var against = term != null ? term : new UnivTerm(Sort.OMEGA);
    var resultTele = Buffer.<Tuple3<Var, Boolean, Term>>of();
    expr.paramsStream().forEach(tuple -> {
      var result = tuple._2.type().accept(this, against);
      resultTele.append(Tuple.of(tuple._1, tuple._2.explicit(), result.wellTyped));
    });
    var last = expr.last().accept(this, against);
    return new Result(new DT(expr.kind(), Tele.fromBuffer(resultTele), last.wellTyped), against);
  }

  @Rule.Synth
  @Override public Result visitProj(Expr.@NotNull ProjExpr expr, Term term) {
    var tupleRes = expr.tup().accept(this, null);
    if (!(tupleRes.type instanceof DT dt && dt.kind().isSigma))
      return wantButNo(expr.tup(), tupleRes.type, "sigma type");
    var telescope = dt.telescope();
    if (expr.ix() <= 0) {
      // TODO[ice]: too small index
      throw new TyckerException();
    }
    var teleOpt = telescope.skip(expr.ix() - 1);
    // TODO[ice]: too large index
    if (teleOpt.isEmpty()) {
      throw new TyckerException();
    }
    var tele = teleOpt.get();
    var type = tele == null ? dt.last() : tele.type();
    unify(term, type);
    return new Result(new ProjTerm(tupleRes.wellTyped, expr.ix()), type);
  }

  @Override
  public Result catchAll(@NotNull Expr expr, Term term) {
    throw new UnsupportedOperationException(expr.toString());
  }

  public static class TyckerException extends RuntimeException {
  }

  public static record Result(
    @NotNull Term wellTyped,
    @NotNull Term type
  ) {
  }
}
