// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import kala.control.Either;
import kala.tuple.Tuple;
import kala.tuple.Unit;
import kala.value.Ref;
import org.aya.api.error.Reporter;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.Var;
import org.aya.concrete.Expr;
import org.aya.concrete.stmt.Decl;
import org.aya.concrete.stmt.Signatured;
import org.aya.core.Matching;
import org.aya.core.def.*;
import org.aya.core.pat.Pat;
import org.aya.core.sort.LevelSubst;
import org.aya.core.sort.Sort;
import org.aya.core.term.CallTerm;
import org.aya.core.term.FormTerm;
import org.aya.core.term.Term;
import org.aya.core.visitor.Substituter;
import org.aya.generic.GenericBuilder;
import org.aya.generic.Level;
import org.aya.tyck.pat.Conquer;
import org.aya.tyck.pat.PatClassifier;
import org.aya.tyck.pat.PatTycker;
import org.aya.tyck.trace.Trace;
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
    p.localCtx = parent.derive();
    var r = f.apply(yeah, p);
    tracing(Trace.Builder::reduce);
    p.localCtx = parent;
    return r;
  }

  public Def tyck(@NotNull Decl decl) {
    return tyck(decl, newTycker());
  }

  public Def tyck(@NotNull Decl decl, @NotNull ExprTycker tycker) {
    return traced(decl, tycker, this::doTyck);
  }

  private Def doTyck(@NotNull Decl predecl, @NotNull ExprTycker tycker) {
    if (predecl.signature == null) tyckHeader(predecl, tycker);
    var signature = predecl.signature;
    assert signature != null;
    return switch (predecl) {
      case Decl.FnDecl decl -> {
        var factory = FnDef.factory((resultTy, body) ->
          new FnDef(decl.ref, signature.param(), signature.sortParam(), resultTy, body));
        yield decl.body.fold(
          body -> {
            var result = tycker.zonk(body, tycker.inherit(body, signature.result()));
            return factory.apply(result.type(), Either.left(result.wellTyped()));
          },
          clauses -> {
            var patTycker = new PatTycker(tycker);
            var result = patTycker.elabClauses(clauses, signature);
            var matchings = result._2.flatMap(Pat.PrototypeClause::deprototypify);
            var def = factory.apply(result._1, Either.right(matchings));
            if (patTycker.noError())
              ensureConfluent(tycker, signature, result._2, matchings, decl.sourcePos, true);
            return def;
          }
        );
      }
      case Decl.DataDecl decl -> {
        var body = decl.body.map(clause -> traced(clause, tycker, this::visitCtor));
        yield new DataDef(decl.ref, signature.param(), signature.sortParam(), signature.result(), body);
      }
      case Decl.PrimDecl decl -> decl.ref.core;
      case Decl.StructDecl decl -> {
        var result = signature.result();
        yield new StructDef(decl.ref, signature.param(), signature.sortParam(), result, decl.fields.map(field ->
          traced(field, tycker, (f, tyck) -> visitField(f, tyck, result))));
      }
    };
  }

  public void tyckHeader(@NotNull Decl decl, @NotNull ExprTycker tycker) {
    switch (decl) {
      case Decl.FnDecl fn -> {
        tracing(builder -> builder.shift(new Trace.LabelT(fn.sourcePos, "telescope")));
        var resultTele = checkTele(tycker, fn.telescope, FormTerm.freshUniv(fn.sourcePos));
        // It might contain unsolved holes, but that's acceptable.
        var resultRes = tycker.synthesize(fn.result).wellTyped();
        tracing(GenericBuilder::reduce);
        fn.signature = new Def.Signature(tycker.extractLevels(), resultTele, resultRes);
      }
      case Decl.DataDecl data -> {
        var pos = data.sourcePos;
        var tele = checkTele(tycker, data.telescope, FormTerm.freshUniv(pos));
        var result = tycker.zonk(data.result, tycker.inherit(data.result, FormTerm.freshUniv(pos))).wellTyped();
        data.signature = new Def.Signature(tycker.extractLevels(), tele, result);
      }
      case Decl.StructDecl struct -> {
        var pos = struct.sourcePos;
        var tele = checkTele(tycker, struct.telescope, FormTerm.freshUniv(pos));
        var result = tycker.zonk(struct.result, tycker.inherit(struct.result, FormTerm.freshUniv(pos))).wellTyped();
        // var levelSubst = tycker.equations.solve();
        struct.signature = new Def.Signature(tycker.extractLevels(), tele, result);
      }
      case Decl.PrimDecl prim -> {
        assert tycker.localCtx.isEmpty();
        var core = prim.ref.core;
        var tele = checkTele(tycker, prim.telescope, FormTerm.freshUniv(prim.sourcePos));
        if (tele.isNotEmpty()) {
          if (prim.result == null) {
            // TODO[ice]: Expect type and term
            throw new ExprTycker.TyckerException();
          }
          var result = tycker.synthesize(prim.result).wellTyped();
          var levelSubst = new LevelSubst.Simple(MutableMap.create());
          // Homotopy level goes first
          var levels = tycker.extractLevels();
          for (var lvl : core.levels.zip(levels))
            levelSubst.solution().put(lvl._1, new Sort(new Level.Reference<>(lvl._2)));
          var target = FormTerm.Pi.make(core.telescope(), core.result())
            .subst(Substituter.TermSubst.EMPTY, levelSubst);
          tycker.unifyTyReported(FormTerm.Pi.make(tele, result), target, prim.result);
          prim.signature = new Def.Signature(levels, tele, result);
        } else if (prim.result != null) {
          var result = tycker.synthesize(prim.result).wellTyped();
          tycker.unifyTyReported(result, core.result(), prim.result);
        } else prim.signature = new Def.Signature(ImmutableSeq.empty(), core.telescope(), core.result());
        tycker.solveMetas();
      }
    }
  }

  private @NotNull CtorDef visitCtor(Decl.@NotNull DataCtor ctor, ExprTycker tycker) {
    var dataRef = ctor.dataRef;
    var dataSig = dataRef.concrete.signature;
    assert dataSig != null;
    var dataArgs = dataSig.param().map(Term.Param::toArg);
    var sortParam = dataSig.sortParam();
    var dataCall = new CallTerm.Data(dataRef, sortParam.view()
      .map(Level.Reference::new)
      .map(Sort::new)
      .toImmutableSeq(), dataArgs);
    var sig = new Ref<>(new Def.Signature(sortParam, dataSig.param(), dataCall));
    var patTycker = new PatTycker(tycker);
    var pat = patTycker.visitPatterns(sig, ctor.patterns);
    var tele = checkTele(tycker, ctor.telescope.map(param ->
      param.mapExpr(expr -> expr.accept(patTycker.refSubst, Unit.unit()))), dataSig.result());
    var patSubst = patTycker.refSubst.clone();
    var dataParamView = dataSig.param().view();
    if (pat.isNotEmpty()) {
      var subst = dataParamView.map(Term.Param::ref)
        .zip(pat.view().map(Pat::toTerm))
        .<Var, Term>toImmutableMap();
      dataCall = (CallTerm.Data) dataCall.subst(subst);
    }
    var signature = new Def.Signature(sortParam, tele, dataCall);
    ctor.signature = signature;
    var elabClauses = patTycker.elabClauses(patSubst, signature, ctor.clauses);
    var matchings = elabClauses.flatMap(Pat.PrototypeClause::deprototypify);
    var implicits = pat.isEmpty() ? dataParamView.map(Term.Param::implicitify).toImmutableSeq() : Pat.extractTele(pat);
    var elaborated = new CtorDef(dataRef, ctor.ref, pat, implicits, tele, matchings, dataCall, ctor.coerce);
    if (patTycker.noError())
      ensureConfluent(tycker, signature, elabClauses, matchings, ctor.sourcePos, false);
    return elaborated;
  }

  private void ensureConfluent(
    ExprTycker tycker, Def.Signature signature, ImmutableSeq<Pat.PrototypeClause> elabClauses,
    ImmutableSeq<@NotNull Matching> matchings, @NotNull SourcePos pos,
    boolean coverage
  ) {
    if (!matchings.isNotEmpty()) return;
    tracing(builder -> builder.shift(new Trace.LabelT(pos, "confluence check")));
    var classification = PatClassifier.classify(elabClauses, signature.param(),
      tycker.state, tycker.reporter, pos, coverage);
    PatClassifier.confluence(elabClauses, tycker, pos, signature.result(), classification);
    Conquer.against(matchings, tycker, pos, signature);
    tycker.solveMetas();
    tracing(GenericBuilder::reduce);
  }

  private @NotNull FieldDef visitField(Decl.@NotNull StructField field, ExprTycker tycker, @NotNull Term structResult) {
    var tele = checkTele(tycker, field.telescope, structResult);
    var structRef = field.structRef;
    var result = tycker.zonk(field.result, tycker.inherit(field.result, structResult)).wellTyped();
    var structSig = structRef.concrete.signature;
    assert structSig != null;
    field.signature = new Def.Signature(structSig.sortParam(), tele, result);
    var patTycker = new PatTycker(tycker);
    var elabClauses = patTycker.elabClauses(null, field.signature, field.clauses);
    var matchings = elabClauses.flatMap(Pat.PrototypeClause::deprototypify);
    var body = field.body.map(e -> tycker.inherit(e, result).wellTyped());
    var elaborated = new FieldDef(structRef, field.ref, structSig.param(), tele, result, matchings, body, field.coerce);
    if (patTycker.noError())
      ensureConfluent(tycker, field.signature, elabClauses, matchings, field.sourcePos, false);
    return elaborated;
  }

  private @NotNull ImmutableSeq<Term.Param>
  checkTele(@NotNull ExprTycker exprTycker, @NotNull ImmutableSeq<Expr.Param> tele, @NotNull Term univ) {
    var okTele = tele.map(param -> {
      assert param.type() != null; // guaranteed by AyaProducer
      var paramTyped = exprTycker.inherit(param.type(), univ).wellTyped();
      exprTycker.localCtx.put(param.ref(), paramTyped);
      return Tuple.of(new Term.Param(param.ref(), paramTyped, param.explicit()), param.sourcePos());
    });
    exprTycker.solveMetas();
    return okTele.map(tt -> {
      var t = tt._1;
      var term = t.type().zonk(exprTycker, tt._2);
      exprTycker.localCtx.put(t.ref(), term);
      return new Term.Param(t.ref(), term, t.explicit());
    });
  }

  public static class HeaderOnlyException extends RuntimeException {
  }
}
