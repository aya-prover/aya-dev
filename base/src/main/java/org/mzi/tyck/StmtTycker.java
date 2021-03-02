// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.mzi.tyck;

import org.glavo.kala.collection.immutable.ImmutableHashMap;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.collection.mutable.MutableHashMap;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.error.Reporter;
import org.mzi.concrete.Decl;
import org.mzi.concrete.Expr;
import org.mzi.core.def.DataDef;
import org.mzi.core.def.Def;
import org.mzi.core.def.FnDef;
import org.mzi.core.term.PiTerm;
import org.mzi.core.term.Term;
import org.mzi.core.term.UnivTerm;
import org.mzi.generic.Pat;

import java.util.stream.Stream;

public record StmtTycker(@NotNull Reporter reporter) implements Decl.Visitor<Unit, Def> {
  @Override public DataDef visitDataDecl(Decl.@NotNull DataDecl decl, Unit unit) {
    var ctorBuf = Buffer.<DataDef.Ctor>of();
    var clauseBuf = MutableHashMap.<Pat<Term>, DataDef.Ctor>of();
    var checker = new ExprTycker(reporter);
    var tele = checkTele(checker, decl.telescope)
      .collect(ImmutableSeq.factory());
    final var result = checker.checkExpr(decl.result, UnivTerm.OMEGA).wellTyped();
    decl.body.accept(new Decl.DataBody.Visitor<Unit, Unit>() {
      @Override public Unit visitCtor(Decl.DataBody.@NotNull Ctors ctors, Unit unit) {
        ctors.ctors().forEach(ctor -> {
          var tele = checkTele(checker, ctor.telescope);
          ctorBuf.append(new DataDef.Ctor(
            ctor.ref,
            tele.collect(ImmutableSeq.factory()),
            ctor.elim,
            ctor.clauses.stream()
              .map(i -> i.mapTerm(expr -> checker.checkExpr(expr, UnivTerm.OMEGA).wellTyped()))
              .collect(Buffer.factory()),
            ctor.coerce
          ));
        });
        return unit;
      }

      @Override public Unit visitClause(Decl.DataBody.@NotNull Clauses clauses, Unit unit) {
        // TODO[ice]: implement
        throw new UnsupportedOperationException();
      }
    }, unit);
    return new DataDef(decl.ref, tele, result, Buffer.of(), ctorBuf, ImmutableHashMap.from(clauseBuf));
  }

  @Override public FnDef visitFnDecl(Decl.@NotNull FnDecl decl, Unit unit) {
    var headerChecker = new ExprTycker(reporter);
    var resultTele = checkTele(headerChecker, decl.telescope)
      .collect(ImmutableSeq.factory());
    // It might contain unsolved holes, but that's acceptable.
    var resultRes = decl.result.accept(headerChecker, null);

    var bodyChecker = new ExprTycker(headerChecker.metaContext, headerChecker.localCtx);
    var declType = buildFnType(resultTele, resultRes.wellTyped());
    bodyChecker.localCtx.put(decl.ref, declType);

    var bodyRes = headerChecker.checkExpr(decl.body, resultRes.wellTyped());
    return new FnDef(decl.ref, resultTele, bodyRes.type(), bodyRes.wellTyped());
  }

  private @NotNull Term buildFnType(ImmutableSeq<Term.Param> tele, Term result) {
    if (tele.isEmpty()) {
      return result;
    }
    return new PiTerm(false, tele.first(), buildFnType(tele.drop(1), result));
  }

  private @NotNull Stream<Term.Param> checkTele(
    @NotNull ExprTycker exprTycker,
    @NotNull ImmutableSeq<Expr.Param> tele
  ) {
    return tele.stream().map(param -> {
      assert param.type() != null; // guaranteed by MziProducer
      var paramRes = exprTycker.checkExpr(param.type(), null);
      exprTycker.localCtx.put(param.ref(), paramRes.wellTyped());
      return new Term.Param(param.ref(), paramRes.wellTyped(), param.explicit());
    });
  }
}
