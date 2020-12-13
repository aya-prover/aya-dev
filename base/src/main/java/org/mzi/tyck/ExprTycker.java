// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.tyck;

import asia.kala.collection.Map;
import asia.kala.collection.mutable.MutableHashMap;
import lombok.AllArgsConstructor;
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

@AllArgsConstructor
public class ExprTycker implements Expr.BaseVisitor<Term, ExprTycker.Result> {
  public final @NotNull Reporter reporter;
  public final @NotNull Map<Var, Term> localCtx;

  public ExprTycker(@NotNull Reporter reporter) {
    this(reporter, new MutableHashMap<>());
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
    var expected = term.splitTeleDT(expr.params().size());
    if (expected._1 == null) {
      // TODO[ice]: the expected type doesn't have enough parameters
      throw new TyckerException();
    }
    // TODO[ice]: add local bindings to context
    var rec = expr.body().accept(this, expected._1);
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
