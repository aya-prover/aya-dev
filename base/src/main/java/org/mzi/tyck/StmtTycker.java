// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.mzi.tyck;

import org.glavo.kala.collection.immutable.ImmutableHashMap;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.collection.mutable.MutableHashMap;
import org.glavo.kala.tuple.Tuple;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.error.Reporter;
import org.mzi.concrete.Decl;
import org.mzi.concrete.Expr;
import org.mzi.concrete.SigItem;
import org.mzi.core.def.DataDef;
import org.mzi.core.def.Def;
import org.mzi.core.def.FnDef;
import org.mzi.core.term.Term;
import org.mzi.core.term.UnivTerm;
import org.mzi.generic.Pat;
import org.mzi.tyck.trace.Trace;

import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * @author ice1000, kiva
 * @apiNote this class does not create {@link ExprTycker} instances itself,
 * but use the one passed to it. {@link StmtTycker#newTycker()} creates instances
 * of expr tyckers.
 */
public record StmtTycker(
  @NotNull Reporter reporter,
  Trace.@Nullable Builder traceBuilder
) implements SigItem.Visitor<ExprTycker, Def> {
  public @NotNull ExprTycker newTycker() {
    final var tycker = new ExprTycker(reporter);
    tycker.traceBuilder = traceBuilder;
    return tycker;
  }

  private void tracing(@NotNull Consumer<Trace.@NotNull Builder> consumer) {
    if (traceBuilder != null) consumer.accept(traceBuilder);
  }

  @Override public void traceEntrance(@NotNull Decl decl, ExprTycker tycker) {
    tracing(builder -> builder.shift(new Trace.DeclT(decl.ref(), decl.sourcePos)));
  }

  @Override public void traceExit(Def def) {
    tracing(Trace.Builder::reduce);
  }

  @Override public DataDef.Ctor visitCtor(Decl.@NotNull DataCtor ctor, ExprTycker tycker) {
    var tele = checkTele(tycker, ctor.telescope);
    return new DataDef.Ctor(
      ctor.ref,
      tele.collect(ImmutableSeq.factory()),
      ctor.elim,
      ctor.clauses.stream()
        .map(i -> i.mapTerm(expr -> tycker.checkExpr(expr, UnivTerm.OMEGA).wellTyped()))
        .collect(Buffer.factory()),
      ctor.coerce
    );
  }

  @Override public DataDef visitDataDecl(Decl.@NotNull DataDecl decl, ExprTycker tycker) {
    var ctorBuf = Buffer.<DataDef.Ctor>of();
    var clauseBuf = MutableHashMap.<Pat<Term>, DataDef.Ctor>of();
    var tele = checkTele(tycker, decl.telescope)
      .collect(ImmutableSeq.factory());
    final var result = tycker.checkExpr(decl.result, UnivTerm.OMEGA).wellTyped();
    decl.signature = Tuple.of(tele, result);
    decl.body.accept(new Decl.DataBody.Visitor<ExprTycker, Unit>() {
      @Override public Unit visitCtors(Decl.DataBody.@NotNull Ctors ctors, ExprTycker tycker) {
        ctors.ctors().forEach(ctor -> ctorBuf.append(visitCtor(ctor, tycker)));
        return Unit.unit();
      }

      @Override public Unit visitClause(Decl.DataBody.@NotNull Clauses clauses, ExprTycker tycker) {
        // TODO[ice]: implement
        throw new UnsupportedOperationException();
      }
    }, tycker);
    return new DataDef(decl.ref, tele, result, Buffer.of(), ctorBuf, ImmutableHashMap.from(clauseBuf));
  }

  @Override public FnDef visitFnDecl(Decl.@NotNull FnDecl decl, ExprTycker tycker) {
    var resultTele = checkTele(tycker, decl.telescope)
      .collect(ImmutableSeq.factory());
    // It might contain unsolved holes, but that's acceptable.
    var resultRes = decl.result.accept(tycker, null);
    decl.signature = Tuple.of(resultTele, resultRes.wellTyped());

    var bodyRes = tycker.checkExpr(decl.body, resultRes.wellTyped());
    return new FnDef(decl.ref, resultTele, bodyRes.type(), bodyRes.wellTyped());
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
