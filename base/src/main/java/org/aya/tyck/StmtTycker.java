// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import kala.control.Either;
import org.aya.api.error.Reporter;
import org.aya.concrete.Expr;
import org.aya.concrete.stmt.Decl;
import org.aya.concrete.stmt.Signatured;
import org.aya.core.TypedMatching;
import org.aya.core.def.*;
import org.aya.core.pat.Pat;
import org.aya.core.sort.LevelSubst;
import org.aya.core.sort.Sort;
import org.aya.core.term.CallTerm;
import org.aya.core.term.FormTerm;
import org.aya.core.term.Term;
import org.aya.core.visitor.Substituter;
import org.aya.generic.Level;
import org.aya.generic.Modifier;
import org.aya.tyck.error.PrimProblem;
import org.aya.tyck.pat.Conquer;
import org.aya.tyck.pat.PatClassifier;
import org.aya.tyck.pat.PatTycker;
import org.aya.tyck.trace.Trace;
import org.aya.util.TreeBuilder;
import org.aya.util.error.SourcePos;
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
    else predecl.signature.param().forEach(tycker.localCtx::put);
    var signature = predecl.signature;
    return switch (predecl) {
      case Decl.FnDecl decl -> {
        assert signature != null;
        var factory = FnDef.factory((resultTy, body) ->
          new FnDef(decl.ref, signature.param(), signature.sortParam(), resultTy, decl.modifiers, body));
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
              def = factory.apply(result.result(), Either.right(result.matchings().map(TypedMatching::toMatching)));
              if (patTycker.noError())
                ensureConfluent(tycker, signature, result, pos, true);
            } else {
              // First-match semantics.
              var result = patTycker.elabClausesClassified(clauses, signature, decl.result.sourcePos(), pos);
              def = factory.apply(result.result(), Either.right(result.matchings().map(TypedMatching::toMatching)));
              if (patTycker.noError()) Conquer.against(result.matchings(), true, tycker, pos, signature);
            }
            return def;
          }
        );
      }
      case Decl.DataDecl decl -> {
        assert signature != null;
        var body = decl.body.map(clause -> traced(clause, tycker, this::visitCtor));
        yield new DataDef(decl.ref, signature.param(), signature.sortParam(), decl.sort, body);
      }
      case Decl.PrimDecl decl -> decl.ref.core;
      case Decl.StructDecl decl -> {
        assert signature != null;
        var body = decl.fields.map(field -> traced(field, tycker, this::visitField));
        yield new StructDef(decl.ref, signature.param(), signature.sortParam(), decl.sort, body);
      }
    };
  }

  // Apply a simple checking strategy for maximal metavar inference.
  public @NotNull FnDef simpleFn(@NotNull ExprTycker tycker, Decl.FnDecl fn) {
    return traced(fn, tycker, (o, w) -> doSimpleFn(tycker, fn));
  }

  private @NotNull FnDef doSimpleFn(@NotNull ExprTycker tycker, Decl.FnDecl fn) {
    var okTele = checkTele(tycker, fn.telescope, FormTerm.freshSort(fn.sourcePos));
    var preresult = tycker.synthesize(fn.result).wellTyped();
    var levels = tycker.extractLevels();
    var bodyExpr = fn.body.getLeftValue();
    var prebody = tycker.inherit(bodyExpr, preresult).wellTyped();
    tycker.solveMetas();
    var zonker = tycker.newZonker();
    var result = zonker.zonk(preresult, fn.result.sourcePos());
    var tele = zonkTele(tycker, okTele);
    fn.signature = new Def.Signature(levels, tele, result);
    var body = zonker.zonk(prebody, bodyExpr.sourcePos());
    return new FnDef(fn.ref, tele, levels, result, fn.modifiers, Either.left(body));
  }

  public void tyckHeader(@NotNull Decl decl, @NotNull ExprTycker tycker) {
    tracing(builder -> builder.shift(new Trace.LabelT(decl.sourcePos, "telescope")));
    switch (decl) {
      case Decl.FnDecl fn -> {
        var resultTele = tele(tycker, fn.telescope, FormTerm.freshSort(fn.sourcePos));
        // It might contain unsolved holes, but that's acceptable.
        var resultRes = tycker.synthesize(fn.result).wellTyped().freezeHoles(tycker.state);
        fn.signature = new Def.Signature(tycker.extractLevels(), resultTele, resultRes);
      }
      case Decl.DataDecl data -> {
        var pos = data.sourcePos;
        var tele = tele(tycker, data.telescope, FormTerm.freshSort(pos));
        var result = data.result instanceof Expr.HoleExpr ? FormTerm.Univ.ZERO
          // ^ probably omitted
          : tycker.zonk(data.result, tycker.inherit(data.result, FormTerm.freshUniv(pos))).wellTyped();
        data.signature = new Def.Signature(tycker.extractLevels(), tele, result);
        data.sort = tycker.sort(decl.result, result);
      }
      case Decl.StructDecl struct -> {
        var pos = struct.sourcePos;
        var tele = tele(tycker, struct.telescope, FormTerm.freshSort(pos));
        var result = tycker.zonk(struct.result, tycker.inherit(struct.result, FormTerm.freshUniv(pos))).wellTyped();
        // var levelSubst = tycker.equations.solve();
        struct.signature = new Def.Signature(tycker.extractLevels(), tele, result);
        struct.sort = tycker.sort(decl.result, result);
      }
      case Decl.PrimDecl prim -> {
        assert tycker.localCtx.isEmpty();
        var core = prim.ref.core;
        var tele = tele(tycker, prim.telescope, FormTerm.freshSort(prim.sourcePos));
        if (tele.isNotEmpty()) {
          // ErrorExpr on prim.result means the result type is unspecified.
          if (prim.result instanceof Expr.ErrorExpr) {
            reporter.report(new PrimProblem.NoResultTypeError(prim));
            return;
          }
          var result = tycker.synthesize(prim.result).wellTyped();
          var levelSubst = new LevelSubst.Simple(MutableMap.create());
          var levels = tycker.extractLevels();
          // We assume that there aren't many level parameters in prims (at most 1).
          for (var lvl : core.levels.zip(levels))
            levelSubst.solution().put(lvl._2, new Sort(new Level.Reference<>(lvl._1)));
          result = result.subst(Substituter.TermSubst.EMPTY, levelSubst);
          tele = Term.Param.subst(tele, levelSubst);
          tycker.unifyTyReported(
            FormTerm.Pi.make(tele, result),
            FormTerm.Pi.make(core.telescope, core.result),
            prim.result);
          prim.signature = new Def.Signature(core.levels, tele, result);
        } else if (!(prim.result instanceof Expr.ErrorExpr)) {
          var result = tycker.synthesize(prim.result).wellTyped();
          tycker.unifyTyReported(result, core.result, prim.result);
        } else prim.signature = new Def.Signature(core.levels, core.telescope, core.result);
        tycker.solveMetas();
      }
    }
    tracing(TreeBuilder::reduce);
  }

  private @NotNull CtorDef visitCtor(Decl.@NotNull DataCtor ctor, ExprTycker tycker) {
    var dataRef = ctor.dataRef;
    var dataConcrete = dataRef.concrete;
    var dataSig = dataConcrete.signature;
    var dataSort = dataConcrete.sort;
    assert dataSig != null;
    var dataArgs = dataSig.param().map(Term.Param::toArg);
    var sortParam = dataSig.sortParam();
    var dataCall = new CallTerm.Data(dataRef, sortParam.view()
      .map(Level.Reference::new)
      .map(Sort::new)
      .toImmutableSeq(), dataArgs);
    var sig = new Def.Signature(sortParam, dataSig.param(), dataCall);
    var patTycker = new PatTycker(tycker);
    // There might be patterns in the constructor
    var pat = ctor.patterns.isNotEmpty()
      ? patTycker.visitPatterns(sig, ctor.patterns.view())._1
      // No patterns, leave it blank
      : ImmutableSeq.<Pat>empty();
    var tele = tele(tycker, ctor.telescope, dataSort);
    var signature = new Def.Signature(sortParam, tele, dataCall);
    ctor.signature = signature;
    var dataTeleView = dataSig.param().view();
    if (pat.isNotEmpty()) {
      dataCall = (CallTerm.Data) dataCall.subst(ImmutableMap.from(
        dataTeleView.map(Term.Param::ref).zip(pat.view().map(Pat::toTerm))));
    }
    ctor.patternTele = pat.isEmpty() ? dataTeleView.map(Term.Param::implicitify).toImmutableSeq() : Pat.extractTele(pat);
    var elabClauses = patTycker.elabClausesDirectly(ctor.clauses, signature, ctor.sourcePos);
    var elaborated = new CtorDef(dataRef, ctor.ref, pat, ctor.patternTele, tele, elabClauses.matchings().map(TypedMatching::toMatching), dataCall, ctor.coerce);
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

  private @NotNull FieldDef visitField(Decl.@NotNull StructField field, ExprTycker tycker) {
    var structRef = field.structRef;
    var structSort = structRef.concrete.sort;
    var structSig = structRef.concrete.signature;
    assert structSig != null;
    var tele = tele(tycker, field.telescope, structSort);
    var result = tycker.zonk(field.result, tycker.inherit(field.result, new FormTerm.Univ(structSort))).wellTyped();
    field.signature = new Def.Signature(structSig.sortParam(), tele, result);
    var patTycker = new PatTycker(tycker);
    var clauses = patTycker.elabClausesDirectly(field.clauses, field.signature, field.result.sourcePos());
    var body = field.body.map(e -> tycker.inherit(e, result).wellTyped());
    var elaborated = new FieldDef(structRef, field.ref, structSig.param(), tele, result, clauses.matchings().map(TypedMatching::toMatching), body, field.coerce);
    if (patTycker.noError())
      ensureConfluent(tycker, field.signature, clauses, field.sourcePos, false);
    return elaborated;
  }

  private @NotNull ImmutableSeq<Term.Param>
  tele(@NotNull ExprTycker tycker, @NotNull ImmutableSeq<Expr.Param> tele, Sort sort) {
    var okTele = checkTele(tycker, tele, sort);
    tycker.solveMetas();
    return zonkTele(tycker, okTele);
  }

  private record TeleResult(@NotNull Term.Param param, @NotNull SourcePos pos) {}

  private @NotNull ImmutableSeq<TeleResult>
  checkTele(@NotNull ExprTycker exprTycker, @NotNull ImmutableSeq<Expr.Param> tele, Sort sort) {
    return tele.map(param -> {
      assert param.type() != null; // guaranteed by AyaProducer
      var paramTyped = exprTycker.inherit(param.type(), new FormTerm.Univ(sort)).wellTyped();
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
