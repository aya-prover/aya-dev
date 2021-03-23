// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck;

import org.aya.api.error.Reporter;
import org.aya.api.ref.Var;
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
import org.aya.generic.GenericBuilder;
import org.aya.tyck.pat.PatClassifier;
import org.aya.tyck.pat.PatTycker;
import org.aya.tyck.trace.Trace;
import org.aya.util.FP;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.control.Either;
import org.glavo.kala.value.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    tycker.localCtx = tycker.localCtx.derive();
  }

  @Override public void traceExit(ExprTycker tycker, Def def) {
    tracing(Trace.Builder::reduce);
    var parent = tycker.localCtx.parent();
    assert parent != null;
    tycker.localCtx = parent;
  }

  @Override public DataDef.Ctor visitCtor(Decl.@NotNull DataCtor ctor, ExprTycker tycker) {
    var dataRef = ctor.dataRef;
    var dataSig = dataRef.concrete.signature;
    assert dataSig != null;
    var dataContextArgs = dataSig.contextParam().map(Term.Param::toArg);
    var dataArgs = dataSig.param().map(Term.Param::toArg);
    var dataCall = new CallTerm.Data(dataRef, dataContextArgs, dataArgs);
    var sig = new Ref<>(new Def.Signature(ImmutableSeq.empty(), dataSig.param(), dataCall));
    var pat = new PatTycker(tycker).visitPatterns(sig, ctor.patterns);
    var tele = checkTele(tycker, ctor.telescope);
    var dataParamView = dataSig.param().view();
    if (!pat.isEmpty()) {
      var subst = dataParamView.map(Term.Param::ref)
        .zip(pat.view().map(Pat::toTerm))
        .<Var, Term>toImmutableMap();
      dataCall = (CallTerm.Data) dataCall.subst(subst);
    }
    var signature = new Def.Signature(ImmutableSeq.of(), tele, dataCall);
    ctor.signature = signature;
    var patTycker = new PatTycker(tycker);
    var elabClauses = ctor.clauses
      .map(c -> patTycker.visitMatch(c, signature));
    var clauses = elabClauses.flatMap(Pat.PrototypeClause::deprototypify);
    var implicits = pat.isEmpty() ? dataParamView.map(Term.Param::implicitify) : Pat.extractTele(pat);
    var fullTele = implicits.concat(tele).toImmutableSeq();
    var elaborated = new DataDef.Ctor(dataRef, ctor.ref, pat, fullTele, tele, clauses, dataCall, ctor.coerce);
    if (!elabClauses.isEmpty()) {
      var classification = PatClassifier.classify(elabClauses, tycker.metaContext.reporter(), ctor.sourcePos, false);
      PatClassifier.confluence(elabClauses, tycker.metaContext, ctor.sourcePos, signature.result(), classification);
    }
    return elaborated;
  }

  @Override public DataDef visitData(Decl.@NotNull DataDecl decl, ExprTycker tycker) {
    var ctxTele = tycker.localCtx.extract();
    var tele = checkTele(tycker, decl.telescope);
    final var result = tycker.checkExpr(decl.result, UnivTerm.OMEGA).wellTyped();
    decl.signature = new Def.Signature(ctxTele, tele, result);
    var body = decl.body.map(clause -> visitCtor(clause, tycker));
    var collectedBody = body.collect(ImmutableSeq.factory());
    return new DataDef(decl.ref, ctxTele, tele, result, collectedBody);
  }

  @Override public StructDef visitStruct(Decl.@NotNull StructDecl decl, ExprTycker tycker) {
    var ctxTele = tycker.localCtx.extract();
    var tele = checkTele(tycker, decl.telescope);
    final var result = tycker.checkExpr(decl.result, UnivTerm.OMEGA).wellTyped();
    decl.signature = new Def.Signature(ctxTele, tele, result);
    return new StructDef(decl.ref, ctxTele, tele, result, decl.fields.map(field -> visitField(field, tycker)));
  }

  @Override
  public StructDef.Field visitField(Decl.@NotNull StructField field, ExprTycker tycker) {
    var tele = checkTele(tycker, field.telescope);
    var structRef = field.structRef;
    var result = field.result.accept(tycker, null).wellTyped();
    // TODO[kiva]: ctxTele?
    field.signature = new Def.Signature(ImmutableSeq.of(), tele, result);
    var body = field.body.map(e -> e.accept(tycker, result).wellTyped());
    return new StructDef.Field(structRef, field.ref, tele, result, body, field.coerce);
  }

  @Override public FnDef visitFn(Decl.@NotNull FnDecl decl, ExprTycker tycker) {
    var ctxTele = tycker.localCtx.extract();
    tracing(builder -> builder.shift(new Trace.LabelT(decl.sourcePos, "telescope")));
    var resultTele = checkTele(tycker, decl.telescope);
    // It might contain unsolved holes, but that's acceptable.
    var resultRes = decl.result.accept(tycker, null);
    tracing(GenericBuilder::reduce);
    var signature = new Ref<>(new Def.Signature(ctxTele, resultTele, resultRes.wellTyped()));
    decl.signature = signature.value;
    var patTycker = new PatTycker(tycker);
    var what = FP.distR(decl.body.map(
      left -> tycker.checkExpr(left, resultRes.wellTyped()).toTuple(),
      right -> patTycker.elabClause(right, signature)));
    var resultTy = what._1;
    if (what._2.isLeft())
      return new FnDef(decl.ref, ctxTele, resultTele, resultTy, Either.left(what._2.getLeftValue()));
    var cs = what._2.getRightValue();
    var elabClauses = cs.flatMap(Pat.PrototypeClause::deprototypify);
    if (!cs.isEmpty()) {
      var classification = PatClassifier.classify(cs, tycker.metaContext.reporter(), decl.sourcePos, true);
      var elaborated = new FnDef(decl.ref, ctxTele, resultTele, resultTy, Either.right(elabClauses));
      PatClassifier.confluence(cs, tycker.metaContext, decl.sourcePos, resultTy, classification);
      return elaborated;
    } else return new FnDef(decl.ref, ctxTele, resultTele, resultTy, Either.right(elabClauses));
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
