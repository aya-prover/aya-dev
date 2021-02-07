// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.tyck;

import org.glavo.kala.Unit;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.error.Reporter;
import org.mzi.concrete.Decl;
import org.mzi.concrete.Expr;
import org.mzi.concrete.Param;
import org.mzi.core.def.Def;
import org.mzi.core.def.FnDef;
import org.mzi.core.term.Term;

public class StmtTycker implements Decl.Visitor<Unit, Def> {
  public final @NotNull Reporter reporter;

  public StmtTycker(@NotNull Reporter reporter) {
    this.reporter = reporter;
  }

  @Override public Def visitDataDecl(Decl.@NotNull DataDecl decl, Unit unit) {
    // TODO[kiva]: implement
    throw new UnsupportedOperationException();
  }

  @Override public Def visitFnDecl(Decl.@NotNull FnDecl decl, Unit unit) {
    // TODO[kiva]: is it ok to reuse the exprTycker?
    ExprTycker exprTycker = new ExprTycker(reporter);

    var resultTele = checkTele(exprTycker, decl.telescope);

    Term resultType;
    Term resultBody;

    // the function doesn't have a result type annotation
    if (decl.result instanceof Expr.HoleExpr) {
      var bodyRes = exprTycker.checkExpr(decl.body, null);
      resultBody = bodyRes.wellTyped();
      resultType = bodyRes.type();
    } else {
      var resultRes = exprTycker.checkExpr(decl.result, null);
      resultType = resultRes.wellTyped();
      var bodyRes = exprTycker.checkExpr(decl.body, resultType);
      resultBody = bodyRes.wellTyped();
    }

    var def = new FnDef(decl.ref.name(), resultTele, resultType, resultBody);
    decl.wellTyped = def;
    return def;
  }

  private @NotNull ImmutableSeq<org.mzi.core.Param> checkTele(@NotNull ExprTycker exprTycker,
                                                              @NotNull Buffer<Param> tele) {
    return tele.stream()
      .map(param -> {
        assert param.type() != null; // guaranteed by MziProducer
        var paramRes = exprTycker.checkExpr(param.type(), null);
        exprTycker.localCtx.put(param.var(), paramRes.wellTyped());
        return new org.mzi.core.Param(param.var(), paramRes.wellTyped(), param.explicit());
      })
      .collect(ImmutableSeq.factory());
  }
}
