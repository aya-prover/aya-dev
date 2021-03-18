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
import org.aya.core.def.StructDef;
import org.aya.core.pat.Pat;
import org.aya.core.term.CallTerm;
import org.aya.core.term.Term;
import org.aya.core.term.UnivTerm;
import org.aya.tyck.pat.PatClassifier;
import org.aya.tyck.pat.PatTycker;
import org.aya.tyck.trace.Trace;
import org.aya.util.FP;
import org.glavo.kala.collection.SeqLike;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.control.Option;
import org.glavo.kala.tuple.Tuple;
import org.glavo.kala.value.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

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
    return checkTele(tycker, ctor.telescope, tele -> {
      var dataRef = ctor.dataRef;
      var dataContextArgs = Objects.requireNonNull(dataRef.concrete.signature)
        .contextParam().view().map(Term.Param::toArg);
      var dataArgs = Objects.requireNonNull(dataRef.concrete.signature)
        .param().view().map(Term.Param::toArg);
      // TODO[ice]: insert data params?
      var signature = new Def.Signature(ImmutableSeq.of(), tele, new CallTerm.Data(
        dataRef,
        dataContextArgs,
        dataArgs));
      ctor.signature = signature;
      var patTycker = new PatTycker(tycker);
      var elabClauses = ctor.clauses
        .map(c -> patTycker.visitMatch(c, signature)._2);
      // TODO[ice]: confluence check
      var clauses = elabClauses.flatMap(Pat.Clause::fromProto);
      return new DataDef.Ctor(dataRef, ctor.ref, tele, clauses, ctor.coerce);
    });
  }

  @Override public DataDef visitData(Decl.@NotNull DataDecl decl, ExprTycker tycker) {
    var ctxTele = tycker.localCtx.extract();
    return checkTele(tycker, decl.telescope, tele -> {
      final var result = tycker.checkExpr(decl.result, UnivTerm.OMEGA).wellTyped();
      decl.signature = new Def.Signature(ctxTele, tele, result);
      var body = decl.body.map(clause -> {
        var recover = tycker.localCtx.localMap();
        var patTyck = new PatTycker(tycker);
        var pat = clause._1.map(pattern -> pattern.accept(patTyck, decl.signature.param().first().type()));
        var ctor = visitCtor(clause._2, tycker);
        if (clause._1.isDefined()) {
          tycker.localCtx.localMap().clear();
          tycker.localCtx.localMap().putAll(recover);
        }
        return Tuple.of(pat, ctor);
      });
      return new DataDef(decl.ref, ctxTele, tele, result, body
        .collect(ImmutableSeq.factory()));
    });
  }

  @Override public StructDef visitStruct(Decl.@NotNull StructDecl decl, ExprTycker tycker) {
    var ctxTele = tycker.localCtx.extract();
    return checkTele(tycker, decl.telescope, tele -> {
      final var result = tycker.checkExpr(decl.result, UnivTerm.OMEGA).wellTyped();
      decl.signature = new Def.Signature(ctxTele, tele, result);
      return new StructDef(decl.ref, ctxTele, tele, result, decl.fields.map(field -> visitField(field, tycker)));
    });
  }

  @Override
  public StructDef.Field visitField(Decl.@NotNull StructField field, ExprTycker tycker) {
    return checkTele(tycker, field.telescope, tele -> {
      var structRef = field.structRef;
      var result = field.result.accept(tycker, null).wellTyped();
      // TODO[kiva]: ctxTele?
      field.signature = new Def.Signature(ImmutableSeq.of(), tele, result);
      var body = field.body.map(e -> e.accept(tycker, result).wellTyped());
      return new StructDef.Field(structRef, field.ref, tele, result, body, field.coerce);
    });
  }

  @Override public FnDef visitFn(Decl.@NotNull FnDecl decl, ExprTycker tycker) {
    var ctxTele = tycker.localCtx.extract();
    return checkTele(tycker, decl.telescope, resultTele -> {
      // It might contain unsolved holes, but that's acceptable.
      var resultRes = decl.result.accept(tycker, null);
      var signature = new Ref<>(new Def.Signature(ctxTele, resultTele, resultRes.wellTyped()));
      decl.signature = signature.value;
      var patTycker = new PatTycker(tycker);
      var what = FP.distR(decl.body.map(
        left -> tycker.checkExpr(left, resultRes.wellTyped()).toTuple(),
        right -> patTycker.elabClause(right, signature)));
      var body = what._2.mapRight(cs -> {
        if (!cs.isEmpty())
          PatClassifier.test(cs, reporter, decl.sourcePos);
        return cs.flatMap(Pat.Clause::fromProto);
      });
      return new FnDef(decl.ref, ctxTele, resultTele, what._1, body);
    });
  }

  private <T> T
  checkParam(@NotNull ExprTycker exprTycker, @NotNull Expr.Param param, @NotNull Function<Term.Param, T> action) {
    assert param.type() != null;
    var paramRes = exprTycker.checkExpr(param.type(), null);
    return exprTycker.localCtx.with(param.ref(), paramRes.wellTyped(),
      () -> action.apply(new Term.Param(param.ref(), paramRes.wellTyped(), param.explicit())));
  }

  private <T> T
  checkTele(@NotNull ExprTycker exprTycker, @NotNull SeqLike<Expr.Param> tele, @NotNull Function<ImmutableSeq<Term.Param>, T> action) {
    if (tele.isEmpty()) return action.apply(ImmutableSeq.of());
    return checkParam(exprTycker, tele.first(), param ->
      checkTele(exprTycker, tele.view().drop(1), params ->
        action.apply(params.prepended(param))
      )
    );
  }
}
