// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Either;
import kala.control.Option;
import org.aya.generic.Modifier;
import org.aya.primitive.PrimFactory;
import org.aya.primitive.ShapeFactory;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.concrete.Pattern;
import org.aya.syntax.concrete.stmt.decl.*;
import org.aya.syntax.core.def.*;
import org.aya.syntax.core.pat.Pat;
import org.aya.syntax.core.pat.PatToTerm;
import org.aya.syntax.core.term.*;
import org.aya.syntax.core.term.call.DataCall;
import org.aya.syntax.core.term.xtt.DimTyTerm;
import org.aya.syntax.core.term.xtt.EqTerm;
import org.aya.syntax.ref.LocalCtx;
import org.aya.tyck.ctx.LocalLet;
import org.aya.tyck.error.*;
import org.aya.tyck.pat.ClauseTycker;
import org.aya.tyck.pat.IApplyConfl;
import org.aya.tyck.pat.PatClassifier;
import org.aya.tyck.pat.YouTrack;
import org.aya.tyck.tycker.Problematic;
import org.aya.tyck.tycker.TeleTycker;
import org.aya.util.error.WithPos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

import static org.aya.tyck.tycker.TeleTycker.loadTele;

public record StmtTycker(
  @NotNull Reporter reporter,
  @NotNull ShapeFactory shapeFactory,
  @NotNull PrimFactory primFactory
) implements Problematic {
  private @NotNull ExprTycker mkTycker() {
    return new ExprTycker(new TyckState(shapeFactory, primFactory), new LocalCtx(), new LocalLet(), reporter);
  }
  public @NotNull TyckDef check(Decl predecl) {
    ExprTycker tycker = null;
    if (predecl instanceof Decl decl) {
      if (decl.ref().signature == null) tycker = checkHeader(decl);
    }

    return switch (predecl) {
      case FnDecl fnDecl -> {
        var fnRef = fnDecl.ref;
        assert fnRef.signature != null;

        var factory = FnDef.factory(body -> new FnDef(fnRef, fnDecl.modifiers, body));
        var teleVars = fnDecl.telescope.map(Expr.Param::ref);

        yield switch (fnDecl.body) {
          case FnBody.ExprBody(var expr) -> {
            var signature = fnRef.signature;
            // In the ordering, we guarantee that expr bodied fn are always checked as a whole
            assert tycker != null;
            var result = tycker.inherit(expr, tycker.whnf(signature.result().instantiateTeleVar(teleVars.view())))
              // we still need to bind [result.type()] in case it was a hole
              .bindTele(teleVars.view());
            tycker.solveMetas();
            fnRef.signature = fnRef.signature.descent(tycker::zonk);
            yield factory.apply(Either.left(tycker.zonk(result.wellTyped())));
          }
          case FnBody.BlockBody(var clauses, var elims) -> {
            var signature = fnRef.signature;
            // we do not load signature here, so we need a fresh ExprTycker
            var clauseTycker = new ClauseTycker.Worker(new ClauseTycker(tycker = mkTycker()),
              teleVars, signature, clauses, elims, true);

            var orderIndependent = fnDecl.modifiers.contains(Modifier.Overlap);
            FnDef def;
            ClauseTycker.TyckResult patResult;
            if (orderIndependent) {
              // Order-independent.
              patResult = clauseTycker.checkNoClassify();
              def = factory.apply(Either.right(patResult.wellTyped()));
              if (!patResult.hasLhsError()) {
                var rawParams = signature.rawParams();
                var confluence = new YouTrack(rawParams, tycker, fnDecl.sourcePos());
                confluence.check(patResult, signature.result(),
                  PatClassifier.classify(patResult.clauses().view(), rawParams.view(), tycker, fnDecl.sourcePos()));
              }
            } else {
              patResult = clauseTycker.check(fnDecl.entireSourcePos());
              def = factory.apply(Either.right(patResult.wellTyped()));
            }
            if (!patResult.hasLhsError()) new IApplyConfl(def, tycker, fnDecl.sourcePos()).check();
            yield def;
          }
        };
      }
      case DataCon con -> Objects.requireNonNull(con.ref.core);   // see checkHeader
      case PrimDecl prim -> Objects.requireNonNull(prim.ref.core);
      case DataDecl data -> {
        var sig = data.ref.signature;
        assert sig != null;
        for (var kon : data.body) checkHeader(kon);
        yield new DataDef(data.ref, data.body.map(kon -> kon.ref.core));
      }
    };
  }

  public ExprTycker checkHeader(@NotNull Decl decl) {
    var tycker = mkTycker();
    switch (decl) {
      case DataCon con -> checkKitsune(con, tycker);
      case PrimDecl prim -> checkPrim(tycker, prim);
      case DataDecl data -> {
        var teleTycker = new TeleTycker.Default(tycker);
        var result = data.result;
        if (result == null) result = new WithPos<>(data.sourcePos(), new Expr.Type(0));
        var signature = teleTycker.checkSignature(data.telescope, result);
        tycker.solveMetas();
        signature = signature.descent(tycker::zonk);
        var sort = SortTerm.Type0;
        if (signature.result() instanceof SortTerm userSort) sort = userSort;
        else fail(BadTypeError.univ(tycker.state, result, signature.result()));
        data.ref.signature = new Signature(signature.param(), sort);
      }
      case FnDecl fn -> {
        var teleTycker = new TeleTycker.Default(tycker);
        var result = fn.result;
        if (result == null) result = new WithPos<>(fn.sourcePos(), new Expr.Hole(false, null));
        var fnRef = fn.ref;
        fnRef.signature = teleTycker.checkSignature(fn.telescope, result);
        // For ExprBody, they will be zonked later
        if (fn.body instanceof FnBody.BlockBody(var cls, _)) {
          tycker.solveMetas();
          fnRef.signature = fnRef.signature.descent(tycker::zonk);
          if (fnRef.signature.param().isEmpty() && cls.isEmpty())
            fail(new NobodyError(decl.sourcePos(), fn.ref));
        }
      }
    }
    return tycker;
  }

  /**
   * Kitsune says kon!
   *
   * @apiNote invoke this method after loading the telescope of data!
   */
  private void checkKitsune(@NotNull DataCon dataCon, @NotNull ExprTycker tycker) {
    var ref = dataCon.ref;
    if (ref.core != null) return;
    var conDecl = ref.concrete;
    var dataRef = dataCon.dataRef;
    var dataSig = dataRef.signature;
    assert dataSig != null : "the header of data should be tycked";
    var ownerTele = dataSig.param().map(x -> x.descent((_, p) -> p.implicitize()));
    var ownerBinds = dataRef.concrete.telescope.map(Expr.Param::ref);
    // dataTele already in localCtx
    // The result that a con should be, unless it is a Path result
    var freeDataCall = new DataCall(dataRef, 0, ownerBinds.map(FreeTerm::new));

    var wellPats = ImmutableSeq.<Pat>empty();
    if (dataCon.patterns.isNotEmpty()) {
      // do not do coverage check
      var lhsResult = new ClauseTycker(tycker = mkTycker()).checkLhs(dataSig, null,
        new Pattern.Clause(dataCon.entireSourcePos(), dataCon.patterns, Option.none()), false);
      wellPats = lhsResult.clause().pats();
      tycker.setLocalCtx(lhsResult.localCtx());
      lhsResult.addLocalLet(ownerBinds, tycker);
      // TODO: inline pattern / terms
      freeDataCall = new DataCall(dataRef, 0, wellPats.map(PatToTerm::visit));

      var allBinds = Pat.collectBindings(wellPats.view());
      ownerTele = allBinds
        .map(x -> new WithPos<>(x.var().definition(),
          new Param(x.var().name(), x.type(), false)));
      ownerBinds = allBinds.map(Pat.CollectBind::var);
      if (wellPats.allMatch(pat -> pat instanceof Pat.Bind))
        wellPats = ImmutableSeq.empty();
    } else {
      loadTele(ownerBinds.view(), dataSig, tycker);
    }

    var teleTycker = new TeleTycker.Con(tycker, (SortTerm) dataSig.result());
    var selfTele = teleTycker.checkTele(conDecl.telescope);
    var selfTeleVars = conDecl.teleVars();

    var conTy = conDecl.result;
    EqTerm boundaries = null;
    if (conTy != null) {
      var tyResult = tycker.whnf(tycker.ty(conTy));
      if (tyResult instanceof EqTerm eq) {
        var state = tycker.state;
        var fresh = new FreeTerm("i");
        tycker.unifyTermReported(eq.appA(fresh), freeDataCall, null, conTy.sourcePos(),
          cmp -> new UnifyError.ConReturn(conDecl, cmp, new UnifyInfo(state)));

        selfTele = selfTele.appended(new WithPos<>(conTy.sourcePos(),
          new Param("i", DimTyTerm.INSTANCE, true)));
        selfTeleVars = selfTeleVars.appended(fresh.name());
        boundaries = eq;
      } else {
        var state = tycker.state;
        tycker.unifyTermReported(tyResult, freeDataCall, null, conTy.sourcePos(), cmp ->
          new UnifyError.ConReturn(conDecl, cmp, new UnifyInfo(state)));
      }
    }
    tycker.solveMetas();

    // the result will refer to the telescope of con if it has patterns,
    // the path result may also refer to it, so we need to bind both
    var boundDataCall = (DataCall) tycker.zonk(freeDataCall).bindTele(selfTeleVars);
    if (boundaries != null) boundaries = (EqTerm) tycker.zonk(boundaries).bindTele(selfTeleVars);
    var boundariesWithDummy = boundaries != null ? boundaries : ErrorTerm.DUMMY;
    var selfSig = new Signature(tycker.zonk(selfTele), new TupTerm(
      // This is a silly hack that allows two terms to appear in the result of a Signature
      // I considered using `AppTerm` but that is more disgraceful
      ImmutableSeq.of(boundDataCall, boundariesWithDummy))).bindTele(ownerBinds.view());
    var selfSigResult = ((TupTerm) selfSig.result()).items();
    boundDataCall = (DataCall) selfSigResult.get(0);
    if (boundaries != null) boundaries = (EqTerm) selfSigResult.get(1);

    // The signature of con should be full (the same as [konCore.telescope()])
    ref.signature = new Signature(ownerTele.concat(selfSig.param()), boundDataCall);
    ref.core = new ConDef(dataRef, ref, wellPats, boundaries,
      ownerTele.map(WithPos::data),
      selfSig.rawParams(),
      boundDataCall, false);
  }

  private void checkPrim(@NotNull ExprTycker tycker, PrimDecl prim) {
    var teleTycker = new TeleTycker.Default(tycker);
    // This directly corresponds to the tycker.localCtx = new LocalCtx();
    //  at the end of this case clause.
    assert tycker.localCtx().isEmpty();
    var primRef = prim.ref;
    var core = primRef.core;
    if (prim.telescope.isEmpty() && prim.result == null) {
      var pos = prim.sourcePos();
      primRef.signature = new Signature(core.telescope().map(param -> new WithPos<>(pos, param)), core.result());
      return;
    }
    if (prim.telescope.isNotEmpty()) {
      if (prim.result == null) {
        fail(new PrimError.NoResultType(prim));
        return;
      }
    }
    assert prim.result != null;
    var tele = teleTycker.checkSignature(prim.telescope, prim.result);
    tycker.unifyTermReported(
      PiTerm.make(tele.param().view().map(p -> p.data().type()), tele.result()),
      // No checks, slightly faster than TeleDef.defType
      PiTerm.make(core.telescope.view().map(Param::type), core.result),
      null, prim.entireSourcePos(),
      msg -> new PrimError.BadSignature(prim, msg, new UnifyInfo(tycker.state)));
    primRef.signature = tele.descent(tycker::zonk);
    tycker.solveMetas();
    tycker.setLocalCtx(new LocalCtx());
  }
}
