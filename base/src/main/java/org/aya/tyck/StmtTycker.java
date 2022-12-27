// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableArrayList;
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
import org.aya.core.term.*;
import org.aya.core.visitor.Subst;
import org.aya.core.visitor.Zonker;
import org.aya.generic.Modifier;
import org.aya.generic.util.NormalizeMode;
import org.aya.guest0x0.cubical.Partial;
import org.aya.tyck.env.SeqLocalCtx;
import org.aya.tyck.error.*;
import org.aya.tyck.pat.Conquer;
import org.aya.tyck.pat.PatClassifier;
import org.aya.tyck.pat.PatTycker;
import org.aya.tyck.trace.Trace;
import org.aya.tyck.unify.DoubleChecker;
import org.aya.util.Arg;
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
 * but use the one passed to it. {@link StmtTycker#newTycker} creates instances
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
    if (predecl instanceof Decl.Telescopic<?> decl
      && predecl.ref().core == null // for constructors: they do not have signature(body).
      && decl.signature() == null
    ) tyckHeader(predecl, tycker);
    return switch (predecl) {
      case ClassDecl classDecl -> throw new UnsupportedOperationException("ClassDecl is not supported yet");
      case TeleDecl.FnDecl decl -> {
        var signature = decl.signature;
        assert signature != null;
        var factory = FnDef.factory((resultTy, body) ->
          new FnDef(decl.ref, signature.param(), resultTy, decl.modifiers, body));
        yield decl.body.fold(body -> {
            var nobody = tycker.check(body, signature.result()).wellTyped();
            // It may contain unsolved metas. See `checkTele`.
            var resultTy = tycker.zonk(signature.result());
            return factory.apply(resultTy, Either.left(tycker.zonk(nobody)));
          }, clauses -> {
            var exprTycker = newTycker(tycker.state.primFactory(), tycker.shapeFactory);
            FnDef def;
            var pos = decl.sourcePos;
            PatTycker.PatResult result;
            var orderIndependent = decl.modifiers.contains(Modifier.Overlap);
            if (orderIndependent) {
              // Order-independent.
              result = PatTycker.elabClausesDirectly(exprTycker, clauses, signature);
              def = factory.apply(result.result(), Either.right(result.matchings()));
              if (!result.hasLhsError()) {
                tracing(builder -> builder.shift(new Trace.LabelT(pos, "confluence check")));
                PatClassifier.confluence(result, tycker, pos,
                  PatClassifier.classify(result.clauses(), signature.param(), tycker, pos));
                tracing(TreeBuilder::reduce);
              }
            } else {
              // First-match semantics.
              result = PatTycker.elabClausesClassified(exprTycker, clauses, signature, pos);
              def = factory.apply(result.result(), Either.right(result.matchings()));
            }
            if (!result.hasLhsError()) Conquer.against(def, orderIndependent, tycker, pos);
            return def;
          }
        );
      }
      case TeleDecl.DataDecl decl -> {
        var signature = decl.signature;
        assert signature != null;
        var body = decl.body.map(clause -> (CtorDef) tyck(clause, tycker));
        yield new DataDef(decl.ref, signature.param(), signature.result(), body);
      }
      case TeleDecl.PrimDecl decl -> decl.ref.core;
      case TeleDecl.StructDecl decl -> {
        var signature = decl.signature;
        assert signature != null;
        var body = decl.fields.map(field -> (FieldDef) tyck(field, tycker));
        yield new StructDef(decl.ref, signature.param(), signature.result(), body);
      }
      // Do nothing, data constructors is just a header.
      case TeleDecl.DataCtor ctor -> ctor.ref.core;
      case TeleDecl.StructField field -> {
        // TODO[ice]: remove this hack
        if (field.ref.core != null) yield field.ref.core;
        var signature = field.signature;
        assert signature != null; // already handled in the entrance of this method
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
      var bodyExpr = fn.body.getLeftValue();
      Term preresult, prebody;
      if (fn.result != null) {
        preresult = tycker.synthesize(fn.result).wellTyped();
        prebody = tycker.check(bodyExpr, preresult).wellTyped();
      } else {
        var synthesize = tycker.synthesize(bodyExpr);
        prebody = synthesize.wellTyped();
        preresult = synthesize.type();
      }
      return new Tmp(okTele, preresult, prebody);
    });
    var tele = zonkTele(tycker, tmp.okTele);
    var result = tycker.zonk(tmp.preresult);
    fn.signature = new Def.Signature<>(tele, result);
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
        if (fn.result == null) fn.result = new Expr.Hole(fn.sourcePos, false, null);
        var resultRes = tycker.ty(fn.result).freezeHoles(tycker.state);
        // We cannot solve metas in result type from clauses,
        //  because when we're in the clauses, the result type is substituted,
        //  and it doesn't make sense to solve a "substituted meta"
        // In the future, we may generate a "constant" meta and try to solve it
        //  if the result type is a pure meta.
        if (fn.body.isRight()) {
          var tele = MutableArrayList.from(resultTele);
          resultRes = PiTerm.unpi(tycker.zonk(resultRes), tycker::whnf, tele);
          resultTele = tele.toImmutableArray();
        }
        fn.signature = new Def.Signature<>(resultTele, resultRes);
        if (resultTele.isEmpty() && fn.body.isRight() && fn.body.getRightValue().isEmpty())
          reporter.report(new NobodyError(decl.sourcePos(), fn.ref));
      }
      case TeleDecl.DataDecl data -> {
        var tele = tele(tycker, data.telescope, null);
        var resultTy = resultTy(tycker, data);
        data.signature = new Def.Signature<>(tele, resultTy);
      }
      case TeleDecl.StructDecl struct -> {
        var tele = tele(tycker, struct.telescope, null);
        var result = resultTy(tycker, struct);
        struct.signature = new Def.Signature<>(tele, result);
      }
      case TeleDecl.PrimDecl prim -> {
        // This directly corresponds to the tycker.localCtx = new LocalCtx();
        //  at the end of this case clause.
        assert tycker.localCtx.isEmpty();
        var core = prim.ref.core;
        var tele = tele(tycker, prim.telescope, null);
        if (tele.isNotEmpty()) {
          // ErrorExpr on prim.result means the result type is unspecified.
          if (prim.result == null) {
            reporter.report(new PrimError.NoResultType(prim));
            return;
          }
          var result = tycker.synthesize(prim.result).wellTyped();
          // We assume that there aren't many level parameters in prims (at most 1).
          tycker.unifyTyReported(
            PiTerm.make(tele, result),
            PiTerm.make(core.telescope, core.result),
            prim.result);
          prim.signature = new Def.Signature<>(tele, result);
        } else if (prim.result != null) {
          var result = tycker.synthesize(prim.result).wellTyped();
          tycker.unifyTyReported(result, core.result, prim.result);
        } else prim.signature = new Def.Signature<>(core.telescope, core.result);
        tycker.solveMetas();
        tycker.localCtx = new SeqLocalCtx();
      }
      case TeleDecl.DataCtor ctor -> checkCtor(tycker, ctor);
      case TeleDecl.StructField field -> {
        if (field.signature != null) break;
        var structRef = field.structRef;
        var structSig = structRef.concrete.signature;
        assert structSig != null;
        var structLvl = structSig.result();
        var tele = tele(tycker, field.telescope, structLvl.isProp() ? null : structLvl);
        var result = tycker.zonk(structLvl.isProp() ? tycker.ty(field.result) : tycker.inherit(field.result, structLvl).wellTyped());
        field.signature = new Def.Signature<>(tele, result);
      }
    }
    tracing(TreeBuilder::reduce);
  }

  /** Extracted for the extreme complexity. */
  private void checkCtor(@NotNull ExprTycker tycker, TeleDecl.DataCtor ctor) {
    if (ctor.ref.core != null) return;
    var dataRef = ctor.dataRef;
    var dataConcrete = dataRef.concrete;
    var dataSig = dataConcrete.signature;
    assert dataSig != null;
    var dataArgs = dataSig.param().map(Term.Param::toArg);
    var predataCall = new DataCall(dataRef, 0, dataArgs);
    // There might be patterns in the constructor
    var pat = ImmutableSeq.<Arg<Pat>>empty();
    if (ctor.patterns.isNotEmpty()) {
      var sig = new Def.Signature<>(dataSig.param(), predataCall);
      var lhs = PatTycker.checkLhs(tycker,
        new Pattern.Clause(ctor.sourcePos, ctor.patterns, Option.none()), sig, false, false);
      pat = lhs.preclause().patterns();
      // Revert to the "after patterns" state
      tycker.localCtx = lhs.gamma();
      tycker.lets = lhs.bodySubst();
      predataCall = (DataCall) predataCall.subst(new Subst(
        dataSig.param().view().map(Term.Param::ref),
        pat.view().map(Arg::term).map(Pat::toTerm)));
    }
    // Because we need to use in a lambda expression later
    var dataCall = predataCall;
    var ulift = dataConcrete.signature.result();
    var tele = tele(tycker, ctor.telescope, ulift.isProp() ? null : ulift);

    var elabClauses = tycker.zonk(tycker.elaboratePartial(ctor.clauses, dataCall));

    // Users may have written the result type explicitly
    if (ctor.result != null) {
      // At this point, they may contain metas
      var result = tycker.ty(ctor.result).normalize(tycker.state, NormalizeMode.NF);
      var additionalTele = MutableArrayList.<Term.Param>create();
      result = PiTerm.unpi(result, tycker::whnf, additionalTele);
      Partial<Term> partial = null;
      if (result instanceof PathTerm path) {
        var flat = path.flatten();
        additionalTele.appendAll(flat.computeParams());
        // Also can contain metas
        partial = flat.partial();
        result = flat.type();
      }
      var eventually = result;
      tycker.unifyTyReported(dataCall, eventually, ctor.result,
        u -> new UnifyError.ConReturn(ctor, u, new UnifyInfo(tycker.state)));
      tycker.solveMetas();
      var zonker = Zonker.make(tycker);
      // Zonk after the unification, because the unification may have solved some metas.
      if (partial != null) partial = partial.map(zonker);
      tele = tele.view()
        .concat(additionalTele)
        .map(p -> p.descent(zonker))
        .toImmutableSeq();

      elabClauses = PartialTerm.merge(Seq.of(elabClauses, partial).filterNotNull());
    }
    var patternTele = pat.isEmpty()
      ? dataSig.param().map(Term.Param::implicitify)
      : Pat.extractTele(pat.map(Arg::term));
    while (!(elabClauses instanceof Partial.Split<Term> split)) {
      reporter.report(new CubicalError.PathConDominateError(ctor.clauses.sourcePos()));
      elabClauses = PartialTerm.DUMMY_SPLIT;
    }
    dataConcrete.checkedBody.append(new CtorDef(dataRef, ctor.ref, pat, patternTele, tele, split, dataCall, ctor.coerce));
  }

  private SortTerm resultTy(@NotNull ExprTycker tycker, TeleDecl<SortTerm> data) {
    if (data.result != null) {
      var result = tycker.sort(data.result);
      return (SortTerm) tycker.zonk(result.wellTyped());
    }
    return SortTerm.Type0;
  }

  /**
   * @param sort If < 0, apply "synthesize" to the types.
   */
  private @NotNull ImmutableSeq<Term.Param>
  tele(@NotNull ExprTycker tycker, @NotNull ImmutableSeq<Expr.Param> tele, @Nullable SortTerm sort) {
    var okTele = tycker.subscoped(() -> checkTele(tycker, tele, sort));
    return zonkTele(tycker, okTele);
  }

  private record TeleResult(@NotNull Term.Param param, @NotNull SourcePos pos) {}


  /**
   * @param tele A type in the telescope of a constructor.
   * @param sort the universe of the data type.
   */
  private @NotNull Term checkTele(@NotNull ExprTycker exprTycker, @NotNull Expr tele, @NotNull SortTerm sort) {
    var result = exprTycker.ty(tele);
    var unifier = exprTycker.unifier(tele.sourcePos(), Ordering.Lt);
    // TODO[isType]: there is no restriction on constructor telescope now
    // new DoubleChecker(unifier).inherit(result, sort);
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
      );
      var newParam = new Term.Param(param, paramTyped);
      exprTycker.localCtx.put(newParam);
      exprTycker.addWithTerm(param, paramTyped);
      return new TeleResult(newParam, param.sourcePos());
    });
  }

  private @NotNull ImmutableSeq<Term.Param> zonkTele(@NotNull ExprTycker exprTycker, ImmutableSeq<TeleResult> okTele) {
    exprTycker.solveMetas();
    var zonker = Zonker.make(exprTycker);
    return okTele.map(tt -> {
      var rawParam = tt.param;
      var param = new Term.Param(rawParam, zonker.apply(rawParam.type()));
      exprTycker.localCtx.put(param);
      return param;
    });
  }
}
