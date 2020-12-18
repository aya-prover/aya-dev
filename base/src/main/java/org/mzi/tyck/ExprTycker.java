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
import org.mzi.tyck.unify.NaiveDefEq;
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

  @Override
  public Result visitLam(Expr.@NotNull LamExpr expr, Term term) {
    if (term == null) {
      var domain = new LocalVar("_");
      var codomain = new LocalVar("_");
      term = new DT(DTKind.Pi, Tele.mock(domain, expr.params().first().explicit()), new AppTerm.HoleApp(codomain));
    }
    if (!(term.normalize(NormalizeMode.WHNF) instanceof DT dt && dt.kind().isPi)) {
      return wantPi(expr, term);
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
      } else wantPi(expr, tyRef.value);
    });
    assert tyRef.value != null;
    var rec = expr.body().accept(this, tyRef.value);
    return new Result(new LamTerm(Tele.fromBuffer(resultTele), rec.wellTyped), dt);
  }

  private <T> T wantPi(Expr.@NotNull LamExpr expr, Term term) {
    reporter.report(new BadTypeError(expr, Doc.plain("pi type"), term));
    throw new TyckerException();
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
