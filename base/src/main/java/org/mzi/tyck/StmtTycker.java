// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.tyck;

import org.glavo.kala.Unit;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.error.Reporter;
import org.mzi.concrete.Decl;
import org.mzi.concrete.Param;
import org.mzi.core.def.Def;
import org.mzi.core.def.FnDef;

import java.util.stream.Stream;

public record StmtTycker(@NotNull Reporter reporter) implements Decl.Visitor<Unit, Def> {
  @Override public Def visitDataDecl(Decl.@NotNull DataDecl decl, Unit unit) {
    // TODO[kiva]: implement
    throw new UnsupportedOperationException();
  }

  @Override public Def visitFnDecl(Decl.@NotNull FnDecl decl, Unit unit) {
    var exprTycker = new ExprTycker(reporter);
    var resultTele = checkTele(exprTycker, decl.telescope)
      .collect(ImmutableSeq.factory());
    // It might contain unsolved holes, but that's acceptable.
    var resultRes = decl.result.accept(exprTycker, null);
    var bodyRes = exprTycker.checkExpr(decl.body, resultRes.wellTyped());
    return new FnDef(decl.ref, resultTele, bodyRes.type(), bodyRes.wellTyped());
  }

  private @NotNull Stream<org.mzi.core.Param> checkTele(
    @NotNull ExprTycker exprTycker,
    @NotNull ImmutableSeq<Param> tele
  ) {
    return tele.stream().map(param -> {
      assert param.type() != null; // guaranteed by MziProducer
      var paramRes = exprTycker.checkExpr(param.type(), null);
      exprTycker.localCtx.put(param.var(), paramRes.wellTyped());
      return new org.mzi.core.Param(param.var(), paramRes.wellTyped(), param.explicit());
    });
  }
}
