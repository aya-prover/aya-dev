// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Either;
import kala.control.Option;
import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.concrete.stmt.ClassDecl;
import org.aya.concrete.stmt.Decl;
import org.aya.concrete.stmt.TeleDecl;
import org.aya.core.def.*;
import org.aya.core.pat.Pat;
import org.aya.core.repr.AyaShape;
import org.aya.core.term.DataCall;
import org.aya.core.term.PiTerm;
import org.aya.core.term.SortTerm;
import org.aya.core.term.Term;
import org.aya.core.visitor.Subst;
import org.aya.generic.Modifier;
import org.aya.generic.SortKind;
import org.aya.guest0x0.cubical.Partial;
import org.aya.tyck.env.SeqLocalCtx;
import org.aya.tyck.error.NobodyError;
import org.aya.tyck.error.PrimError;
import org.aya.tyck.pat.Conquer;
import org.aya.tyck.pat.PatClassifier;
import org.aya.tyck.pat.PatTycker;
import org.aya.tyck.trace.Trace;
import org.aya.util.Ordering;
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
 * but use the one passed to it. {@link StmtTycker#newTycker(PrimDef.Factory, AyaShape.Factory)} creates instances
 * of expr tyckers.
 */
public record StmtTycker(@NotNull Reporter reporter, Trace.@Nullable Builder traceBuilder) {
  public @NotNull ExprTycker newTycker(@NotNull PrimDef.Factory primFactory, @NotNull AyaShape.Factory literalShapes) {
    return new ExprTycker(primFactory, literalShapes, reporter, traceBuilder);
  }

  private void tracing(@NotNull Consumer<Trace.@NotNull Builder> consumer) {
    if (traceBuilder != null) consumer.accept(traceBuilder);
  }

  private <S extends Decl, D extends GenericDef> D
  traced(@NotNull S yeah, ExprTycker p, @NotNull BiFunction<S, ExprTycker, D> f) {
    tracing(builder -> builder.shift(new Trace.DeclT(yeah.ref(), yeah.sourcePos())));
    var r = p.subscoped(() -> f.apply(yeah, p));
    tracing(Trace.Builder::reduce);
    return r;
  }

  public @NotNull GenericDef tyck(@NotNull Decl decl, @NotNull ExprTycker tycker) {
    return traced(decl, tycker, this::doTyck);
  }

  private @NotNull GenericDef doTyck(@NotNull Decl predecl, @NotNull ExprTycker tycker) {
    if (predecl instanceof Decl.Telescopic decl && decl.signature() == null) tyckHeader(predecl, tycker);
    var signature = predecl instanceof Decl.Telescopic decl ? decl.signature() : null;
    return switch (predecl) {
      case ClassDecl classDecl -> throw new UnsupportedOperationException("ClassDecl is not supported yet");
      case TeleDecl.FnDecl decl -> {
        assert signature != null;
        var factory = FnDef.factory((resultTy, body) ->
          new FnDef(decl.ref, signature.param(), resultTy, decl.modifiers, body));
        yield decl.body.fold(
          body -> {
            var nobody = tycker.check(body, signature.result()).wellTyped();
            tycker.solveMetas();
            // It may contain unsolved metas. See `checkTele`.
            var resultTy = tycker.zonk(signature.result());
            return factory.apply(resultTy, Either.left(tycker.zonk(nobody)));
          },
          clauses -> {
            var patTycker = new PatTycker(newTycker(tycker.state.primFactory(), tycker.shapeFactory));
            FnDef def;
            var pos = decl.sourcePos;
            if (decl.modifiers.contains(Modifier.Overlap)) {
              // Order-independent.
              var result = patTycker.elabClausesDirectly(clauses, signature);
              def = factory.apply(result.result(), Either.right(result.matchings()));
              if (patTycker.noError())
                ensureConfluent(tycker, signature, result, pos);
            } else {
              // First-match semantics.
              var result = patTycker.elabClausesClassified(clauses, signature, pos);
              def = factory.apply(result.result(), Either.right(result.matchings()));
              if (patTycker.noError()) Conquer.against(result.matchings(), true, tycker, pos, signature);
            }
            return def;
          }
        );
      }
      case TeleDecl.DataDecl decl -> {
        assert signature != null;
        var body = decl.body.map(clause -> (CtorDef) tyck(clause, tycker));
        yield new DataDef(decl.ref, signature.param(), decl.ulift, body);
      }
      case TeleDecl.PrimDecl decl -> decl.ref.core;
      case TeleDecl.StructDecl decl -> {
        assert signature != null;
        var body = decl.fields.map(field -> (FieldDef) tyck(field, tycker));
        yield new StructDef(decl.ref, signature.param(), decl.ulift, body);
      }
      case TeleDecl.DataCtor ctor -> {
        // TODO[ice]: remove this hack
        if (ctor.ref.core != null) yield ctor.ref.core;
        assert signature == ctor.signature && signature != null; // already handled in the entrance of this method
        var dataRef = ctor.dataRef;
        var dataConcrete = dataRef.concrete;
        var dataSig = dataConcrete.signature;
        assert dataSig != null;
        var dataCall = (DataCall) signature.result();
        var tele = signature.param();
        var pat = ctor.yetTyckedPat;
        assert pat != null; // header should be checked first
        if (pat.isNotEmpty()) dataCall = (DataCall) dataCall.subst(new Subst(
          dataSig.param().view().map(Term.Param::ref),
          pat.view().map(Pat::toTerm)));
        var elabClauses = tycker.zonk(tycker.elaboratePartial(ctor.clauses, dataCall));
        if (!(elabClauses instanceof Partial.Split<Term> split)) {
          throw new AssertionError("This does not seem right, " + elabClauses);
        }
        var elaborated = new CtorDef(dataRef, ctor.ref, pat, ctor.patternTele, tele, split, dataCall, ctor.coerce);
        dataConcrete.checkedBody.append(elaborated);
        yield elaborated;
      }
      case TeleDecl.StructField field -> {
        // TODO[ice]: remove this hack
        if (field.ref.core != null) yield field.ref.core;
        assert signature == field.signature && signature != null; // already handled in the entrance of this method
        var structRef = field.structRef;
        var structSig = structRef.concrete.signature;
        assert structSig != null;
        var result = signature.result();
        var body = field.body.map(e -> tycker.inherit(e, result).wellTyped());
        yield new FieldDef(structRef, field.ref, structSig.param(), signature.param(), result, body, field.coerce);
      }
    };
  }

  // Apply a simple checking strategy for maximal metavar inference.
  public @NotNull FnDef simpleFn(@NotNull ExprTycker tycker, TeleDecl.FnDecl fn) {
    return traced(fn, tycker, this::doSimpleFn);
  }

  private @NotNull FnDef doSimpleFn(TeleDecl.FnDecl fn, @NotNull ExprTycker tycker) {
    record Tmp(ImmutableSeq<TeleResult> okTele, Term preresult, Term prebody) {}
    var tmp = tycker.subscoped(() -> {
      var okTele = checkTele(tycker, fn.telescope, null);
      var preresult = tycker.synthesize(fn.result).wellTyped();
      var bodyExpr = fn.body.getLeftValue();
      var prebody = tycker.check(bodyExpr, preresult).wellTyped();
      tycker.solveMetas();
      return new Tmp(okTele, preresult, prebody);
    });
    var tele = zonkTele(tycker, tmp.okTele);
    var result = tycker.zonk(tmp.preresult);
    fn.signature = new Def.Signature(tele, result);
    var body = tycker.zonk(tmp.prebody);
    return new FnDef(fn.ref, fn.signature.param(), fn.signature.result(), fn.modifiers, Either.left(body));
  }

  public void tyckHeader(@NotNull Decl decl, @NotNull ExprTycker tycker) {
    tracing(builder -> builder.shift(new Trace.LabelT(decl.sourcePos(), "telescope of " + decl.ref().name())));
    switch (decl) {
      case ClassDecl classDecl -> throw new UnsupportedOperationException("ClassDecl is not supported yet");
      case TeleDecl.FnDecl fn -> {
        var resultTele = tele(tycker, fn.telescope, null);
        // It might contain unsolved holes, but that's acceptable.
        var resultRes = tycker.synthesize(fn.result).wellTyped().freezeHoles(tycker.state);
        // We cannot solve metas in result type from clauses,
        //  because when we're in the clauses, the result type is substituted,
        //  and it doesn't make sense to solve a "substituted meta"
        // In the future, we may generate a "constant" meta and try to solve it
        //  if the result type is a pure meta.
        if (fn.body.isRight()) resultRes = tycker.zonk(resultRes);
        fn.signature = new Def.Signature(resultTele, resultRes);
        if (resultTele.isEmpty() && fn.body.isRight() && fn.body.getRightValue().isEmpty())
          reporter.report(new NobodyError(decl.sourcePos(), fn.ref));
      }
      case TeleDecl.DataDecl data -> {
        var tele = tele(tycker, data.telescope, null);
        var resultTy = resultTy(tycker, data);
        data.ulift = resultTy;
        data.signature = new Def.Signature(tele, resultTy);
      }
      case TeleDecl.StructDecl struct -> {
        var tele = tele(tycker, struct.telescope, null);
        var result = resultTy(tycker, struct);
        struct.signature = new Def.Signature(tele, result);
        struct.ulift = result;
      }
      case TeleDecl.PrimDecl prim -> {
        // This directly corresponds to the tycker.localCtx = new LocalCtx();
        //  at the end of this case clause.
        assert tycker.localCtx.isEmpty();
        var core = prim.ref.core;
        var tele = tele(tycker, prim.telescope, null);
        if (tele.isNotEmpty()) {
          // ErrorExpr on prim.result means the result type is unspecified.
          if (prim.result instanceof Expr.Error) {
            reporter.report(new PrimError.NoResultType(prim));
            return;
          }
          var result = tycker.synthesize(prim.result).wellTyped();
          // We assume that there aren't many level parameters in prims (at most 1).
          tycker.unifyTyReported(
            PiTerm.make(tele, result),
            PiTerm.make(core.telescope, core.result),
            prim.result);
          prim.signature = new Def.Signature(tele, result);
        } else if (!(prim.result instanceof Expr.Error)) {
          var result = tycker.synthesize(prim.result).wellTyped();
          tycker.unifyTyReported(result, core.result, prim.result);
        } else prim.signature = new Def.Signature(core.telescope, core.result);
        tycker.solveMetas();
        tycker.localCtx = new SeqLocalCtx();
      }
      case TeleDecl.DataCtor ctor -> {
        if (ctor.signature != null) return;
        var dataRef = ctor.dataRef;
        var dataConcrete = dataRef.concrete;
        var dataSig = dataConcrete.signature;
        assert dataSig != null;
        var dataArgs = dataSig.param().map(Term.Param::toArg);
        var dataCall = new DataCall(dataRef, 0, dataArgs);
        var sig = new Def.Signature(dataSig.param(), dataCall);
        var patTycker = new PatTycker(tycker);
        // There might be patterns in the constructor
        if (ctor.patterns.isNotEmpty()) {
          var lhs = patTycker.checkLhs(new Pattern.Clause(ctor.sourcePos, ctor.patterns, Option.none()), sig, false);
          ctor.yetTyckedPat = lhs.preclause().patterns();
          // Revert to the "after patterns" state
          tycker.localCtx = lhs.gamma();
          tycker.lets = lhs.bodySubst();
        } else {
          // No patterns, leave it blank
          ctor.yetTyckedPat = ImmutableSeq.empty();
        }
        var tele = tele(tycker, ctor.telescope, dataConcrete.ulift.isProp() ? null : dataConcrete.ulift);
        ctor.signature = new Def.Signature(tele, dataCall);
        ctor.patternTele = ctor.yetTyckedPat.isEmpty()
          ? dataSig.param().map(Term.Param::implicitify)
          : Pat.extractTele(ctor.yetTyckedPat);
      }
      case TeleDecl.StructField field -> {
        if (field.signature != null) return;
        var structRef = field.structRef;
        var structSig = structRef.concrete.signature;
        assert structSig != null;
        var structLvl = structRef.concrete.ulift;
        var tele = tele(tycker, field.telescope, structLvl.isProp() ? null : structLvl);
        var result = tycker.zonk(structLvl.isProp() ? tycker.ty(field.result) : tycker.inherit(field.result, structLvl)).wellTyped();
        field.signature = new Def.Signature(tele, result);
      }
    }
    tracing(TreeBuilder::reduce);
  }

  private SortTerm resultTy(@NotNull ExprTycker tycker, TeleDecl data) {
    SortTerm ret = SortTerm.Type0;
    if (!(data.result instanceof Expr.Hole)) {
      var result = tycker.sort(data.result);
      ret = (SortTerm) tycker.zonk(result.wellTyped());
    }
    return ret;
  }

  private void ensureConfluent(
    ExprTycker tycker, Def.Signature signature,
    PatTycker.PatResult elabClauses, SourcePos pos
  ) {
    tracing(builder -> builder.shift(new Trace.LabelT(pos, "confluence check")));
    PatClassifier.confluence(elabClauses, tycker, pos,
      PatClassifier.classify(elabClauses.clauses(), signature.param(), tycker, pos, true));
    Conquer.against(elabClauses.matchings(), true, tycker, pos, signature);
    tycker.solveMetas();
    tracing(TreeBuilder::reduce);
  }

  /**
   * @param sort If < 0, apply "synthesize" to the types.
   */
  private @NotNull ImmutableSeq<Term.Param>
  tele(@NotNull ExprTycker tycker, @NotNull ImmutableSeq<Expr.Param> tele, @Nullable SortTerm sort) {
    var okTele = tycker.subscoped(() -> checkTele(tycker, tele, sort));
    tycker.solveMetas();
    return zonkTele(tycker, okTele);
  }

  private record TeleResult(@NotNull Term.Param param, @NotNull SourcePos pos) {}

  // similiar to `ExprTycker.sortPi`. `tele` is the domain.
  private @NotNull ExprTycker.Result checkTele(@NotNull ExprTycker exprTycker, @NotNull Expr tele, @NotNull SortTerm sort) {
    var result = exprTycker.ty(tele);
    var unifier = exprTycker.unifier(tele.sourcePos(), Ordering.Lt);
    var ty = result.type();
    switch (ty.kind()) {
      case Type, Set -> unifier.compareSort(ty, sort);
      case Prop -> {
        if (sort.kind() != SortKind.Type) unifier.compareSort(ty, sort);
      }
      case ISet -> {
        if (!sort.kind().hasLevel()) unifier.compareSort(ty, sort);
      }
    }
    return result;
  }

  /**
   * @param sort If == null, apply "synthesize" to the types.
   */
  private @NotNull ImmutableSeq<TeleResult>
  checkTele(@NotNull ExprTycker exprTycker, @NotNull ImmutableSeq<Expr.Param> tele, @Nullable SortTerm sort) {
    return tele.map(param -> {
      var paramTyped = (sort != null
        ? checkTele(exprTycker, param.type(), sort)
        : exprTycker.ty(param.type())
      ).wellTyped();
      var newParam = new Term.Param(param, paramTyped);
      exprTycker.localCtx.put(newParam);
      return new TeleResult(newParam, param.sourcePos());
    });
  }

  private @NotNull ImmutableSeq<Term.Param> zonkTele(@NotNull ExprTycker exprTycker, ImmutableSeq<TeleResult> okTele) {
    return okTele.map(tt -> {
      var rawParam = tt.param;
      var param = new Term.Param(rawParam, exprTycker.zonk(rawParam.type()));
      exprTycker.localCtx.put(param);
      return param;
    });
  }
}
