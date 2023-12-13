// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableArrayList;
import kala.control.Either;
import kala.control.Option;
import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.concrete.stmt.decl.ClassDecl;
import org.aya.concrete.stmt.decl.Decl;
import org.aya.concrete.stmt.decl.TeleDecl;
import org.aya.core.def.*;
import org.aya.core.pat.Pat;
import org.aya.core.term.*;
import org.aya.core.visitor.Subst;
import org.aya.core.visitor.Zonker;
import org.aya.generic.Modifier;
import org.aya.generic.util.NormalizeMode;
import org.aya.guest0x0.cubical.Partial;
import org.aya.tyck.env.SeqLocalCtx;
import org.aya.tyck.error.*;
import org.aya.tyck.pat.ClauseTycker;
import org.aya.tyck.pat.Conquer;
import org.aya.tyck.pat.PatClassifier;
import org.aya.tyck.pat.YouTrack;
import org.aya.tyck.trace.Trace;
import org.aya.tyck.tycker.TracedTycker;
import org.aya.util.Arg;
import org.aya.util.TreeBuilder;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;

/**
 * @author ice1000, kiva
 * @apiNote this class does not create {@link ExprTycker} instances itself,
 * but use the one passed to it. {@link StmtTycker#newTycker} creates instances
 * of expr tyckers.
 */
public final class StmtTycker extends TracedTycker {
  public StmtTycker(@NotNull Reporter reporter, Trace.@Nullable Builder traceBuilder) {
    super(reporter, traceBuilder);
  }

  private <S extends Decl, D extends GenericDef> D
  traced(@NotNull S yeah, ExprTycker p, @NotNull BiFunction<S, ExprTycker, D> f) {
    return traced(() -> new Trace.DeclT(yeah.ref(), yeah.sourcePos()),
      () -> p.subscoped(() -> f.apply(yeah, p)));
  }

  public @NotNull GenericDef tyck(@NotNull Decl decl, @NotNull ExprTycker tycker) {
    return traced(decl, tycker, this::doTyck);
  }

  private @NotNull GenericDef doTyck(@NotNull Decl predecl, @NotNull ExprTycker tycker) {
    if (predecl instanceof TeleDecl<?> decl) {
      if (decl.signature != null) loadTele(tycker, decl.signature);
        // If core == null then not yet tycked. A constructor's signature is always null,
        // so we need this extra check
      else if (predecl.ref().core == null) tyckHeader(predecl, tycker);
    }
    return switch (predecl) {
      case TeleDecl.FnDecl decl -> {
        var signature = decl.signature;
        assert signature != null;
        var factory = FnDef.factory((resultTy, body) ->
          new FnDef(decl.ref, signature.param(), resultTy, decl.modifiers, body));
        yield switch (decl.body) {
          case TeleDecl.ExprBody(var body) -> {

            var nobody = tycker.inherit(body, signature.result()).wellTyped();
            // It may contain unsolved metas. See `checkTele`.
            var resultTy = tycker.zonk(signature.result());
            yield factory.apply(resultTy, Either.left(tycker.zonk(nobody)));
          }
          case TeleDecl.BlockBody(var clauses) -> {
            var exprTycker = newTycker(tycker.state.primFactory(), tycker.shapeFactory);
            FnDef def;
            var pos = decl.sourcePos();
            ClauseTycker.PatResult result;
            var orderIndependent = decl.modifiers.contains(Modifier.Overlap);
            if (orderIndependent) {
              // Order-independent.
              result = ClauseTycker.elabClausesDirectly(exprTycker, clauses, signature);
              def = factory.apply(result.result(), Either.right(result.matchings()));
              if (!result.hasLhsError()) {
                tracing(builder -> builder.shift(new Trace.LabelT(pos, "confluence check")));
                var confluence = new YouTrack(signature.param(), tycker, pos);
                confluence.check(result,
                  PatClassifier.classify(result.clauses(), signature.param(), tycker, pos));
                tracing(TreeBuilder::reduce);
              }
            } else {
              // First-match semantics.
              result = ClauseTycker.elabClausesClassified(exprTycker, clauses, signature, pos);
              def = factory.apply(result.result(), Either.right(result.matchings()));
            }
            if (!result.hasLhsError()) Conquer.against(def, orderIndependent, tycker, pos);
            yield def;
          }
        };
      }
      case TeleDecl.DataDecl decl -> {
        var signature = decl.signature;
        assert signature != null;
        var body = decl.body.map(clause -> (CtorDef) tyck(clause, tycker));
        yield new DataDef(decl.ref, signature.param(), signature.result(), body);
      }
      case TeleDecl.ClassMember member -> {
        if (member.ref.core != null) yield member.ref.core;
        var signature = member.signature;
        assert signature != null; // already handled in the entrance of this method
        var classDef = member.classDef;
        var result = signature.result();
        // var body = member.body.map(e -> tycker.inherit(e, result).wellTyped());
        // ^ TODO[class]: what about this body?
        yield new MemberDef(member.ref, classDef, signature.param(), result, member.coerce);
      }
      // Do nothing, these are just header.
      case TeleDecl.PrimDecl decl -> decl.ref.core;
      case ClassDecl decl -> decl.ref.core;
      case TeleDecl.DataCtor ctor -> ctor.ref.core;
    };
  }

  // Apply a simple checking strategy for maximal metavar inference.
  public @NotNull FnDef simpleFn(@NotNull ExprTycker tycker, TeleDecl.FnDecl fn, Expr expr) {
    return traced(fn, tycker, (f, t) -> doSimpleFn(f, t, expr));
  }

  private @NotNull FnDef doSimpleFn(TeleDecl.FnDecl fn, @NotNull ExprTycker tycker, Expr expr) {
    record Tmp(ImmutableSeq<TeleResult> okTele, Term preresult, Term prebody) {}
    var tmp = tycker.subscoped(() -> {
      var okTele = checkTele(tycker, fn.telescope, null);
      Term preresult, prebody;
      if (fn.result != null) {
        preresult = tycker.ty(fn.result);

        prebody = tycker.inherit(expr, preresult).wellTyped();
      } else {
        var synthesize = tycker.synthesize(expr);
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
    tracing(builder -> builder.shift(new Trace.LabelT(decl.sourcePos(), STR."telescope of \{decl.ref().name()}")));
    switch (decl) {
      case ClassDecl clazz -> {
        var body = clazz.members.map(field -> (MemberDef) tyck(field, tycker));
        // Invoke the constructor, so that `class.ref.core` is set.
        new ClassDef(clazz.ref, body);
      }
      case TeleDecl.FnDecl fn -> {
        var resultTele = tele(tycker, fn.telescope, null);
        // It might contain unsolved holes, but that's acceptable.
        if (fn.result == null) fn.result = new Expr.Hole(fn.sourcePos(), false, null);
        var resultRes = tycker.ty(fn.result).freezeHoles(tycker.state);
        // We cannot solve metas in result type from clauses,
        //  because when we're in the clauses, the result type is substituted,
        //  and it doesn't make sense to solve a "substituted meta"
        // In the future, we may generate a "constant" meta and try to solve it
        //  if the result type is a pure meta.
        if (fn.body instanceof TeleDecl.BlockBody) {
          var tele = MutableArrayList.from(resultTele);
          resultRes = PiTerm.unpi(tycker.zonk(resultRes), tycker::whnf, tele);
          resultTele = tele.toImmutableArray();
        }
        fn.signature = new Def.Signature<>(resultTele, resultRes);
        if (resultTele.isEmpty() && fn.body instanceof TeleDecl.BlockBody(var cls) && cls.isEmpty())
          reporter.report(new NobodyError(decl.sourcePos(), fn.ref));
      }
      case TeleDecl.DataDecl data -> {
        var tele = tele(tycker, data.telescope, null);
        var resultTy = resultTy(tycker, data);
        data.signature = new Def.Signature<>(tele, resultTy);
      }
      case TeleDecl.PrimDecl prim -> {
        // This directly corresponds to the tycker.localCtx = new LocalCtx();
        //  at the end of this case clause.
        assert tycker.ctx.isEmpty();
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
        tycker.ctx = new SeqLocalCtx();
      }
      case TeleDecl.DataCtor ctor -> checkCtor(tycker, ctor);
      case TeleDecl.ClassMember member -> {
        if (member.signature != null) break;
        var tele = tele(tycker, member.telescope, null);
        assert member.result != null;
        var result = tycker.zonk(tycker.ty(member.result));
        member.signature = new Def.Signature<>(tele, result);
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
    loadTele(tycker, dataSig);
    var dataArgs = dataSig.param().map(Term.Param::toArg);
    var predataCall = new DataCall(dataRef, 0, dataArgs);
    // There might be patterns in the constructor
    var pat = ImmutableSeq.<Arg<Pat>>empty();
    if (ctor.patterns.isNotEmpty()) {
      var sig = new Def.Signature<>(dataSig.param(), predataCall);
      var lhs = ClauseTycker.checkLhs(tycker,
        new Pattern.Clause(ctor.sourcePos(), ctor.patterns, Option.none()), sig, false);
      pat = lhs.preclause().patterns();
      // Revert to the "after patterns" state
      tycker.ctx = lhs.gamma();
      tycker.definitionEqualities = lhs.bodySubst();
      predataCall = (DataCall) predataCall.subst(new Subst(
        dataSig.param().view().map(Term.Param::ref),
        pat.view().map(Arg::term).map(Pat::toTerm)));
    }
    // Because we need to use in a lambda expression later
    var dataCall = predataCall;
    var ulift = dataConcrete.signature.result();
    var tele = tele(tycker, ctor.telescope, ulift);

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

  private static void loadTele(@NotNull ExprTycker tycker, Def.Signature<?> dataSig) {
    dataSig.param().forEach(tycker.ctx::put);
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
    if (!exprTycker.synthesizer().inheritPiDom(result, sort))
      reporter.report(new UnifyError.PiDom(tele, result, sort));
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
      exprTycker.ctx.put(newParam);
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
      exprTycker.ctx.put(param);
      return param;
    });
  }
}
