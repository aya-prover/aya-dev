// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.control.Either;
import org.aya.concrete.Expr;
import org.aya.concrete.stmt.Decl;
import org.aya.concrete.stmt.Signatured;
import org.aya.core.def.*;
import org.aya.core.pat.Pat;
import org.aya.core.term.CallTerm;
import org.aya.core.term.FormTerm;
import org.aya.core.term.Term;
import org.aya.generic.Modifier;
import org.aya.tyck.error.PrimProblem;
import org.aya.tyck.pat.Conquer;
import org.aya.tyck.pat.PatClassifier;
import org.aya.tyck.pat.PatTycker;
import org.aya.tyck.trace.Trace;
import org.aya.util.TreeBuilder;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;
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
) {
  public @NotNull ExprTycker newTycker() {
    return new ExprTycker(reporter, traceBuilder);
  }

  private void tracing(@NotNull Consumer<Trace.@NotNull Builder> consumer) {
    if (traceBuilder != null) consumer.accept(traceBuilder);
  }

  private <S extends Signatured, D extends Def> D
  traced(@NotNull S yeah, ExprTycker p, @NotNull BiFunction<S, ExprTycker, D> f) {
    tracing(builder -> builder.shift(new Trace.DeclT(yeah.ref(), yeah.sourcePos)));
    var parent = p.localCtx;
    p.localCtx = parent.deriveMap();
    var r = f.apply(yeah, p);
    tracing(Trace.Builder::reduce);
    p.localCtx = parent;
    return r;
  }

  public @NotNull Def tyck(@NotNull Decl decl, @NotNull ExprTycker tycker) {
    return traced(decl, tycker, this::doTyck);
  }

  private @NotNull Def doTyck(@NotNull Decl predecl, @NotNull ExprTycker tycker) {
    if (predecl.signature == null) tyckHeader(predecl, tycker);
    var signature = predecl.signature;
    return switch (predecl) {
      case Decl.FnDecl decl -> {
        assert signature != null;
        var factory = FnDef.factory((resultTy, body) ->
          new FnDef(decl.ref, signature.param(), resultTy, decl.modifiers, body));
        yield decl.body.fold(
          body -> {
            var nobody = tycker.inherit(body, signature.result()).wellTyped();
            tycker.solveMetas();
            var zonker = tycker.newZonker();
            // It may contain unsolved metas. See `checkTele`.
            var resultTy = zonker.zonk(signature.result(), decl.result.sourcePos());
            return factory.apply(resultTy, Either.left(zonker.zonk(nobody, body.sourcePos())));
          },
          clauses -> {
            var patTycker = new PatTycker(tycker);
            FnDef def;
            var pos = decl.sourcePos;
            if (decl.modifiers.contains(Modifier.Overlap)) {
              // Order-independent.
              var result = patTycker.elabClausesDirectly(clauses, signature, decl.result.sourcePos());
              def = factory.apply(result.result(), Either.right(result.matchings()));
              if (patTycker.noError())
                ensureConfluent(tycker, signature, result, pos, true);
            } else {
              // First-match semantics.
              var result = patTycker.elabClausesClassified(clauses, signature, decl.result.sourcePos(), pos);
              def = factory.apply(result.result(), Either.right(result.matchings()));
              if (patTycker.noError()) Conquer.against(result.matchings(), true, tycker, pos, signature);
            }
            return def;
          }
        );
      }
      case Decl.DataDecl decl -> {
        assert signature != null;
        var body = decl.body.map(clause -> traced(clause, tycker, this::tyck));
        yield new DataDef(decl.ref, signature.param(), decl.ulift, body);
      }
      case Decl.PrimDecl decl -> decl.ref.core;
      case Decl.StructDecl decl -> {
        assert signature != null;
        var body = decl.fields.map(field -> traced(field, tycker, this::tyck));
        yield new StructDef(decl.ref, signature.param(), decl.ulift, body);
      }
    };
  }

  // Apply a simple checking strategy for maximal metavar inference.
  public @NotNull FnDef simpleFn(@NotNull ExprTycker tycker, Decl.FnDecl fn) {
    return traced(fn, tycker, (o, w) -> doSimpleFn(tycker, fn));
  }

  private @NotNull FnDef doSimpleFn(@NotNull ExprTycker tycker, Decl.FnDecl fn) {
    var okTele = checkTele(tycker, fn.telescope, -1);
    var preresult = tycker.synthesize(fn.result).wellTyped();
    var bodyExpr = fn.body.getLeftValue();
    var prebody = tycker.inherit(bodyExpr, preresult).wellTyped();
    tycker.solveMetas();
    var zonker = tycker.newZonker();
    var result = zonker.zonk(preresult, fn.result.sourcePos());
    var tele = zonkTele(tycker, okTele);
    fn.signature = new Def.Signature(tele, result);
    var body = zonker.zonk(prebody, bodyExpr.sourcePos());
    return new FnDef(fn.ref, tele, result, fn.modifiers, Either.left(body));
  }

  public void tyckHeader(@NotNull Decl decl, @NotNull ExprTycker tycker) {
    tracing(builder -> builder.shift(new Trace.LabelT(decl.sourcePos, "telescope")));
    switch (decl) {
      case Decl.FnDecl fn -> {
        var resultTele = tele(tycker, fn.telescope, -1);
        // It might contain unsolved holes, but that's acceptable.
        var resultRes = tycker.synthesize(fn.result).wellTyped().freezeHoles(tycker.state);
        fn.signature = new Def.Signature(resultTele, resultRes);
      }
      case Decl.DataDecl data -> {
        var pos = data.sourcePos;
        var tele = tele(tycker, data.telescope, -1);
        var result = data.result instanceof Expr.HoleExpr ? FormTerm.Univ.ZERO
          // ^ probably omitted
          : tycker.zonk(data.result, tycker.synthesize(data.result)).wellTyped();
        data.signature = new Def.Signature(tele, result);
        // [ice]: this line reports error if result is not a universe term, so we're good
        data.ulift = tycker.ulift(decl.result, result);
      }
      case Decl.StructDecl struct -> {
        var pos = struct.sourcePos;
        var tele = tele(tycker, struct.telescope, -1);
        var result = tycker.zonk(struct.result, tycker.synthesize(struct.result)).wellTyped();
        struct.signature = new Def.Signature(tele, result);
        // [ice]: this line reports error if result is not a universe term, so we're good
        struct.ulift = tycker.ulift(decl.result, result);
      }
      case Decl.PrimDecl prim -> {
        assert tycker.localCtx.isEmpty();
        var core = prim.ref.core;
        var tele = tele(tycker, prim.telescope, -1);
        if (tele.isNotEmpty()) {
          // ErrorExpr on prim.result means the result type is unspecified.
          if (prim.result instanceof Expr.ErrorExpr) {
            reporter.report(new PrimProblem.NoResultTypeError(prim));
            return;
          }
          var result = tycker.synthesize(prim.result).wellTyped();
          // We assume that there aren't many level parameters in prims (at most 1).
          tycker.unifyTyReported(
            FormTerm.Pi.make(tele, result),
            FormTerm.Pi.make(core.telescope, core.result),
            prim.result);
          prim.signature = new Def.Signature(tele, result);
        } else if (!(prim.result instanceof Expr.ErrorExpr)) {
          var result = tycker.synthesize(prim.result).wellTyped();
          tycker.unifyTyReported(result, core.result, prim.result);
        } else prim.signature = new Def.Signature(core.telescope, core.result);
        tycker.solveMetas();
      }
    }
    tracing(TreeBuilder::reduce);
  }

  public void tyckHeader(Decl.@NotNull DataCtor ctor, ExprTycker tycker) {
    if (ctor.signature != null) return;
    var dataRef = ctor.dataRef;
    var dataConcrete = dataRef.concrete;
    var dataSig = dataConcrete.signature;
    assert dataSig != null;
    var dataArgs = dataSig.param().map(Term.Param::toArg);
    var dataCall = new CallTerm.Data(dataRef, 0, dataArgs);
    var sig = new Def.Signature(dataSig.param(), dataCall);
    var patTycker = new PatTycker(tycker);
    // There might be patterns in the constructor
    var pat = ctor.patterns.isNotEmpty()
      ? patTycker.visitPatterns(sig, ctor.patterns.view())._1
      // No patterns, leave it blank
      : ImmutableSeq.<Pat>empty();
    var tele = tele(tycker, ctor.telescope, dataConcrete.ulift);
    ctor.signature = new Def.Signature(tele, dataCall);
    ctor.yetTycker = patTycker;
    ctor.yetTyckedPat = pat;
    var dataTeleView = dataSig.param().view();
    if (pat.isNotEmpty()) {
      dataCall = (CallTerm.Data) dataCall.subst(ImmutableMap.from(
        dataTeleView.map(Term.Param::ref).zip(pat.view().map(Pat::toTerm))));
    }
    ctor.patternTele = pat.isEmpty() ? dataTeleView.map(Term.Param::implicitify).toImmutableSeq() : Pat.extractTele(pat);
  }

  @NotNull public CtorDef tyck(Decl.@NotNull DataCtor ctor, ExprTycker tycker) {
    // TODO[ice]: remove this hack
    if (ctor.ref.core != null) return ctor.ref.core;
    var dataRef = ctor.dataRef;
    var dataConcrete = dataRef.concrete;
    var dataSig = dataConcrete.signature;
    assert dataSig != null;
    if (ctor.signature == null) tyckHeader(ctor, tycker);
    var signature = ctor.signature;
    var dataCall = ((CallTerm.Data) signature.result());
    var tele = signature.param();
    var patTycker = ctor.yetTycker;
    var pat = ctor.yetTyckedPat;
    assert patTycker != null && pat != null; // header should be checked first
    // PatTycker was created when checking the header with another expr tycker,
    // we should make sure it's the same one here. See comments of ExprTycker.
    assert tycker == patTycker.exprTycker;
    var elabClauses = patTycker.elabClausesDirectly(ctor.clauses, signature, ctor.sourcePos);
    var elaborated = new CtorDef(dataRef, ctor.ref, pat, ctor.patternTele, tele, elabClauses.matchings(), dataCall, ctor.coerce);
    dataConcrete.checkedBody.append(elaborated);
    if (patTycker.noError())
      ensureConfluent(tycker, signature, elabClauses, ctor.sourcePos, false);
    return elaborated;
  }

  private void ensureConfluent(
    ExprTycker tycker, Def.Signature signature,
    PatTycker.PatResult elabClauses, SourcePos pos,
    boolean coverage
  ) {
    if (!coverage && elabClauses.matchings().isEmpty()) return;
    tracing(builder -> builder.shift(new Trace.LabelT(pos, "confluence check")));
    PatClassifier.confluence(elabClauses, tycker, pos,
      PatClassifier.classify(elabClauses.clauses(), signature.param(), tycker, pos, coverage));
    Conquer.against(elabClauses.matchings(), true, tycker, pos, signature);
    tycker.solveMetas();
    tracing(TreeBuilder::reduce);
  }

  public void tyckHeader(Decl.@NotNull StructField field, ExprTycker tycker) {
    if (field.signature != null) return;
    var structRef = field.structRef;
    var structSig = structRef.concrete.signature;
    assert structSig != null;
    var tele = tele(tycker, field.telescope, structRef.concrete.ulift);
    var result = tycker.zonk(field.result, tycker.inherit(field.result, new FormTerm.Univ(structRef.concrete.ulift))).wellTyped();
    field.signature = new Def.Signature(tele, result);
  }

  @NotNull public FieldDef tyck(Decl.@NotNull StructField field, ExprTycker tycker) {
    // TODO[ice]: remove this hack
    if (field.ref.core != null) return field.ref.core;
    var structRef = field.structRef;
    var structSig = structRef.concrete.signature;
    assert structSig != null;
    if (field.signature == null) tyckHeader(field, tycker);
    var signature = field.signature;
    var tele = signature.param();
    var result = signature.result();
    var patTycker = new PatTycker(tycker);
    var clauses = patTycker.elabClausesDirectly(field.clauses, field.signature, field.result.sourcePos());
    var body = field.body.map(e -> tycker.inherit(e, result).wellTyped());
    var elaborated = new FieldDef(structRef, field.ref, structSig.param(), tele, result, clauses.matchings(), body, field.coerce);
    if (patTycker.noError())
      ensureConfluent(tycker, field.signature, clauses, field.sourcePos, false);
    return elaborated;
  }

  /**
   * @param sort If < 0, apply "synthesize" to the types.
   */
  private @NotNull ImmutableSeq<Term.Param>
  tele(@NotNull ExprTycker tycker, @NotNull ImmutableSeq<Expr.Param> tele, int sort) {
    var okTele = checkTele(tycker, tele, sort);
    tycker.solveMetas();
    return zonkTele(tycker, okTele);
  }

  private record TeleResult(@NotNull Term.Param param, @NotNull SourcePos pos) {}

  /**
   * @param sort If < 0, apply "synthesize" to the types.
   */
  private @NotNull ImmutableSeq<TeleResult>
  checkTele(@NotNull ExprTycker exprTycker, @NotNull ImmutableSeq<Expr.Param> tele, int sort) {
    return tele.map(param -> {
      var paramTyped = (sort >= 0
        ? exprTycker.inherit(param.type(), new FormTerm.Univ(sort))
        : exprTycker.synthesize(param.type())
      ).wellTyped();
      var newParam = new Term.Param(param, paramTyped);
      exprTycker.localCtx.put(newParam);
      return new TeleResult(newParam, param.sourcePos());
    });
  }

  private @NotNull ImmutableSeq<Term.Param> zonkTele(@NotNull ExprTycker exprTycker, ImmutableSeq<TeleResult> okTele) {
    var zonker = exprTycker.newZonker();
    return okTele.map(tt -> {
      var rawParam = tt.param;
      var param = new Term.Param(rawParam, zonker.zonk(rawParam.type(), tt.pos));
      exprTycker.localCtx.put(param);
      return param;
    });
  }
}
