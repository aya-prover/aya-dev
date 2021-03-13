// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck;

import org.aya.api.error.Reporter;
import org.aya.concrete.Decl;
import org.aya.concrete.Expr;
import org.aya.concrete.Signatured;
import org.aya.core.def.DataDef;
import org.aya.core.def.Def;
import org.aya.core.def.FnDef;
import org.aya.core.term.AppTerm;
import org.aya.core.term.Term;
import org.aya.core.term.UnivTerm;
import org.aya.tyck.pat.PatTycker;
import org.aya.tyck.trace.Trace;
import org.aya.util.FP;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.value.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * @author ice1000, kiva
 * @apiNote this class does not create {@link ExprTycker} instances itself,
 * but use the one passed to it. {@link StmtTycker#newTycker()} creates instances
 * of expr tyckers.
 */
public record StmtTycker(
  @NotNull Reporter reporter,
  Trace.@Nullable Builder traceBuilder
) implements Signatured.Visitor<ExprTycker, Def> {
  public @NotNull ExprTycker newTycker() {
    final var tycker = new ExprTycker(reporter);
    tycker.traceBuilder = traceBuilder;
    return tycker;
  }

  private void tracing(@NotNull Consumer<Trace.@NotNull Builder> consumer) {
    if (traceBuilder != null) consumer.accept(traceBuilder);
  }

  @Override public void traceEntrance(@NotNull Signatured sig, ExprTycker tycker) {
    tracing(builder -> builder.shift(new Trace.DeclT(sig.ref(), sig.sourcePos)));
  }

  @Override public void traceExit(Def def) {
    tracing(Trace.Builder::reduce);
  }

  @Override public DataDef.Ctor visitCtor(Decl.@NotNull DataCtor ctor, ExprTycker tycker) {
    var tele = checkTele(tycker, ctor.telescope);
    var dataRef = ctor.dataRef;
    var dataArgs = Objects.requireNonNull(dataRef.concrete.signature)
      .param().view().map(Term.Param::toArg);
    var signature = new Def.Signature(tele, new AppTerm.DataCall(dataRef, dataArgs));
    ctor.signature = signature;
    var patTycker = new PatTycker(tycker);
    var elabClauses = ctor.clauses
      .map(c -> c.accept(patTycker, signature)._2);
    return new DataDef.Ctor(dataRef, ctor.ref, tele, elabClauses, ctor.coerce);
  }

  @Override public DataDef visitDataDecl(Decl.@NotNull DataDecl decl, ExprTycker tycker) {
    var tele = checkTele(tycker, decl.telescope);
    final var result = tycker.checkExpr(decl.result, UnivTerm.OMEGA).wellTyped();
    decl.signature = new Def.Signature(tele, result);
    return new DataDef(decl.ref, tele, result, decl.body.map(
      ctors -> {
        // ice: this cast is extremely safe.
        return ctors.ctors().stream()
          .map(ctor -> (DataDef.Ctor) ctor.accept(StmtTycker.this, tycker))
          .collect(ImmutableSeq.factory());
      },
      clauses -> {
        // TODO[ice]: implement
        throw new UnsupportedOperationException();
      }));
  }

  @Override public FnDef visitFnDecl(Decl.@NotNull FnDecl decl, ExprTycker tycker) {
    var resultTele = checkTele(tycker, decl.telescope);
    // It might contain unsolved holes, but that's acceptable.
    var resultRes = decl.result.accept(tycker, null);
    var signature = new Ref<>(new Def.Signature(resultTele, resultRes.wellTyped()));
    decl.signature = signature.value;

    var patTycker = new PatTycker(tycker);
    var what = FP.distR(decl.body.map(
      left -> tycker.checkExpr(left, resultRes.wellTyped()).toTuple(),
      right -> patTycker.elabClause(right, signature)));
    return new FnDef(decl.ref, resultTele, what._1, what._2);
  }

  private @NotNull ImmutableSeq<Term.Param>
  checkTele(@NotNull ExprTycker exprTycker, @NotNull ImmutableSeq<Expr.Param> tele) {
    return tele.map(param -> {
      assert param.type() != null; // guaranteed by AyaProducer
      var paramRes = exprTycker.checkExpr(param.type(), null);
      exprTycker.localCtx.put(param.ref(), paramRes.wellTyped());
      return new Term.Param(param.ref(), paramRes.wellTyped(), param.explicit());
    });
  }
}
