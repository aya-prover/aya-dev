// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck;

import org.aya.api.error.DelayedReporter;
import org.aya.api.error.Reporter;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.Var;
import org.aya.concrete.Decl;
import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.concrete.Signatured;
import org.aya.concrete.visitor.ExprRefSubst;
import org.aya.core.def.*;
import org.aya.core.pat.Pat;
import org.aya.core.sort.Level;
import org.aya.core.term.CallTerm;
import org.aya.core.term.FormTerm;
import org.aya.core.term.Term;
import org.aya.generic.GenericBuilder;
import org.aya.generic.Matching;
import org.aya.tyck.pat.Conquer;
import org.aya.tyck.pat.PatClassifier;
import org.aya.tyck.pat.PatTycker;
import org.aya.tyck.trace.Trace;
import org.aya.util.FP;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.control.Either;
import org.glavo.kala.tuple.Unit;
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
    return new ExprTycker(reporter, traceBuilder);
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
    if (reporter instanceof DelayedReporter r) r.reportNow();
  }

  @Override public PrimDef visitPrim(@NotNull Decl.PrimDecl decl, ExprTycker tycker) {
    if (tycker.localCtx.isNotEmpty()) {
      // TODO[ice]: cannot put prims into local context
      throw new ExprTycker.TyckerException();
    }
    var core = decl.ref.core;
    var tele = checkTele(tycker, decl.telescope);
    if (tele.isNotEmpty()) {
      if (decl.result == null) {
        // TODO[ice]: Expect type and term
        throw new ExprTycker.TyckerException();
      }
      var result = decl.result.accept(tycker, null).wellTyped();
      tycker.unifyTyThrowing(
        FormTerm.Pi.make(false, tele, result),
        FormTerm.Pi.make(false, core.telescope(), core.result()),
        decl.result
      );
      decl.signature = new Def.Signature(ImmutableSeq.empty(), ImmutableSeq.empty(), tele, result);
    } else if (decl.result != null) {
      var result = decl.result.accept(tycker, null).wellTyped();
      tycker.unifyTyThrowing(result, core.result(), decl.result);
    } else decl.signature = new Def.Signature(ImmutableSeq.empty(),
      ImmutableSeq.empty(), core.telescope(), core.result());
    return core;
  }

  @Override public DataDef.Ctor visitCtor(Decl.@NotNull DataCtor ctor, ExprTycker tycker) {
    var dataRef = ctor.dataRef;
    var dataSig = dataRef.concrete.signature;
    assert dataSig != null;
    var dataContextArgs = dataSig.contextParam().map(Term.Param::toArg);
    var dataArgs = dataSig.param().map(Term.Param::toArg);
    var sortParam = dataSig.sortParam();
    var dataCall = new CallTerm.Data(dataRef, dataContextArgs, sortParam.map(Level.Reference::new), dataArgs);
    var sig = new Ref<>(new Def.Signature(ImmutableSeq.empty(), sortParam, dataSig.param(), dataCall));
    var patTycker = new PatTycker(tycker);
    var pat = patTycker.visitPatterns(sig, ctor.patterns);
    var tele = checkTele(tycker, ctor.telescope.map(param ->
      param.mapExpr(expr -> expr.accept(patTycker.subst(), Unit.unit()))));
    var patSubst = patTycker.subst().clone();
    var dataParamView = dataSig.param().view();
    if (pat.isNotEmpty()) {
      var subst = dataParamView.map(Term.Param::ref)
        .zip(pat.view().map(Pat::toTerm))
        .<Var, Term>toImmutableMap();
      dataCall = (CallTerm.Data) dataCall.subst(subst);
    }
    var signature = new Def.Signature(ImmutableSeq.of(), sortParam, tele, dataCall);
    ctor.signature = signature;
    var cumulativeCtx = tycker.localCtx.derive();
    var elabClauses = elabClauses(patTycker, patSubst, signature, cumulativeCtx, ctor.clauses);
    var matchings = elabClauses.flatMap(Pat.PrototypeClause::deprototypify);
    var implicits = pat.isEmpty() ? dataParamView.map(Term.Param::implicitify).toImmutableSeq() : Pat.extractTele(pat);
    var elaborated = new DataDef.Ctor(dataRef, ctor.ref, pat, implicits, tele, matchings, dataCall, ctor.coerce);
    ensureConfluent(tycker, signature, cumulativeCtx, elabClauses, matchings, ctor.sourcePos, false);
    return elaborated;
  }

  private void ensureConfluent(
    ExprTycker tycker, Def.Signature signature, LocalCtx ctx, ImmutableSeq<Pat.PrototypeClause> elabClauses,
    ImmutableSeq<@NotNull Matching<Pat, Term>> matchings, @NotNull SourcePos pos, boolean coverage
  ) {
    if (!matchings.isNotEmpty()) return;
    var classification = PatClassifier.classify(elabClauses, tycker.reporter, pos, coverage);
    PatClassifier.confluence(elabClauses, tycker, pos, signature.result(), classification);
    Conquer.against(matchings, ctx, tycker, pos, signature);
  }

  @NotNull private ImmutableSeq<Pat.PrototypeClause> elabClauses(
    PatTycker patTycker, @Nullable ExprRefSubst patSubst, Def.Signature signature,
    LocalCtx cumulativeCtx, @NotNull ImmutableSeq<Pattern.Clause> clauses
  ) {
    return clauses.map(c -> {
      if (patSubst != null) patTycker.subst().resetTo(patSubst);
      return patTycker.visitMatch(c, signature, cumulativeCtx.localMap());
    });
  }

  @Override public DataDef visitData(Decl.@NotNull DataDecl decl, ExprTycker tycker) {
    var ctxTele = tycker.localCtx.extract();
    var tele = checkTele(tycker, decl.telescope);
    final var result = tycker.checkExpr(decl.result, FormTerm.Univ.OMEGA).wellTyped();
    decl.signature = new Def.Signature(ctxTele, tycker.extractLevels(), tele, result);
    var body = decl.body.map(clause -> visitCtor(clause, tycker));
    var collectedBody = body.collect(ImmutableSeq.factory());
    return new DataDef(decl.ref, ctxTele, tele, tycker.extractLevels(), result, collectedBody);
  }

  @Override public StructDef visitStruct(Decl.@NotNull StructDecl decl, ExprTycker tycker) {
    var ctxTele = tycker.localCtx.extract();
    var tele = checkTele(tycker, decl.telescope);
    final var result = tycker.checkExpr(decl.result, FormTerm.Univ.OMEGA).wellTyped();
    decl.signature = new Def.Signature(ctxTele, tycker.extractLevels(), tele, result);
    return new StructDef(decl.ref, ctxTele, tele, tycker.extractLevels(), result, decl.fields.map(field -> visitField(field, tycker)));
  }

  @Override public StructDef.Field visitField(Decl.@NotNull StructField field, ExprTycker tycker) {
    var tele = checkTele(tycker, field.telescope);
    var structRef = field.structRef;
    var result = field.result.accept(tycker, null).wellTyped();
    field.signature = new Def.Signature(ImmutableSeq.of(), tycker.extractLevels(), tele, result);
    var cumulativeCtx = tycker.localCtx.derive();
    var patTycker = new PatTycker(tycker);
    var elabClauses = elabClauses(patTycker, null, field.signature, cumulativeCtx, field.clauses);
    var matchings = elabClauses.flatMap(Pat.PrototypeClause::deprototypify);
    var body = field.body.map(e -> e.accept(tycker, result).wellTyped());
    var structSig = structRef.concrete.signature;
    assert structSig != null;
    var elaborated = new StructDef.Field(structRef, field.ref, structSig.param(), tele, result, matchings, body, field.coerce);
    ensureConfluent(tycker, field.signature, cumulativeCtx, elabClauses, matchings, field.sourcePos, false);
    return elaborated;
  }

  @Override public FnDef visitFn(Decl.@NotNull FnDecl decl, ExprTycker tycker) {
    var ctxTele = tycker.localCtx.extract();
    tracing(builder -> builder.shift(new Trace.LabelT(decl.sourcePos, "telescope")));
    var resultTele = checkTele(tycker, decl.telescope);
    // It might contain unsolved holes, but that's acceptable.
    var resultRes = decl.result.accept(tycker, null);
    tracing(GenericBuilder::reduce);
    var signature = new Ref<>(new Def.Signature(ctxTele, tycker.extractLevels(), resultTele, resultRes.wellTyped()));
    decl.signature = signature.value;
    var patTycker = new PatTycker(tycker);
    var cumulativeCtx = tycker.localCtx.derive();
    var what = FP.distR(decl.body.map(
      left -> tycker.checkExpr(left, resultRes.wellTyped()).toTuple(),
      right -> patTycker.elabClause(right, signature, cumulativeCtx.localMap())));
    var resultTy = what._1;
    var factory = FnDef.factory(body ->
      new FnDef(decl.ref, ctxTele, resultTele, tycker.extractLevels(), resultTy, body));
    if (what._2.isLeft()) return factory.apply(Either.left(what._2.getLeftValue()));
    var elabClauses = what._2.getRightValue();
    var matchings = elabClauses.flatMap(Pat.PrototypeClause::deprototypify);
    var elaborated = factory.apply(Either.right(matchings));
    ensureConfluent(tycker, signature.value, cumulativeCtx, elabClauses, matchings, decl.sourcePos, true);
    return elaborated;
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
