// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.tyck;

import asia.kala.Tuple;
import asia.kala.Tuple2;
import asia.kala.collection.mutable.Buffer;
import asia.kala.collection.mutable.MutableHashMap;
import asia.kala.collection.mutable.MutableMap;
import asia.kala.ref.Ref;
import lombok.AllArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
import org.mzi.tyck.unify.DefEq;
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
      reporter.report(new BadTypeError(expr, Doc.plain("pi type"), term));
      throw new TyckerException();
    }
    var tyRef = new Ref<>(term);
    var resultTele = Buffer.<Tuple2<Var, Term>>of();
    expr.paramsStream().forEach(tuple -> {
      if (tyRef.value instanceof DT pi && pi.kind().isPi) {
        var type = pi.telescope().type();
        if (tuple._2 != null) {
          var result = tuple._2.accept(this, UnivTerm.OMEGA);
          var comparison = new NaiveDefEq(Ordering.Lt, levelEqns).compare(result.wellTyped, type, UnivTerm.OMEGA);
          if (!comparison) {
            // TODO[ice]: expected type mismatch lambda type annotation
            throw new TyckerException();
          } else type = result.wellTyped;
        }
        // FIXME[glavo]: https://github.com/Glavo/kala-common/issues/3
        resultTele.append(Tuple.of(tuple._1, type));
        localCtx.put(tuple._1, type);
        tyRef.value = pi.dropTeleDT(1);
      } else {
        // TODO[ice]: error message on not enough pi parameters
        throw new TyckerException();
      }
    });
    if (tyRef.value == null) {
      // TODO[ice]: error message on not enough pi parameters
      throw new TyckerException();
    }
    var rec = expr.body().accept(this, tyRef.value);
    // FIXME[ice]: use bindings from `expr.params()`
    return new Result(new LamTerm(dt.telescope(), rec.wellTyped), dt);
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
