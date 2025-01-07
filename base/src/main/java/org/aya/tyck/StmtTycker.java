// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Either;
import kala.control.Option;
import org.aya.generic.Modifier;
import org.aya.generic.term.DTKind;
import org.aya.generic.term.SortKind;
import org.aya.normalize.Finalizer;
import org.aya.pretty.doc.Doc;
import org.aya.primitive.PrimFactory;
import org.aya.primitive.ShapeFactory;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.concrete.Pattern;
import org.aya.syntax.concrete.stmt.decl.*;
import org.aya.syntax.core.def.*;
import org.aya.syntax.core.pat.Pat;
import org.aya.syntax.core.pat.PatToTerm;
import org.aya.syntax.core.term.*;
import org.aya.syntax.core.term.call.ClassCall;
import org.aya.syntax.core.term.call.DataCall;
import org.aya.syntax.core.term.xtt.DimTyTerm;
import org.aya.syntax.core.term.xtt.EqTerm;
import org.aya.syntax.ref.LocalVar;
import org.aya.syntax.ref.MapLocalCtx;
import org.aya.syntax.ref.ModulePath;
import org.aya.syntax.telescope.AbstractTele;
import org.aya.syntax.telescope.Signature;
import org.aya.tyck.error.*;
import org.aya.tyck.pat.ClauseTycker;
import org.aya.tyck.pat.IApplyConfl;
import org.aya.tyck.pat.PatClassifier;
import org.aya.tyck.pat.YouTrack;
import org.aya.tyck.pat.iter.SignatureIterator;
import org.aya.tyck.tycker.Problematic;
import org.aya.tyck.tycker.TeleTycker;
import org.aya.unify.Synthesizer;
import org.aya.util.error.Panic;
import org.aya.util.error.WithPos;
import org.aya.util.reporter.Reporter;
import org.aya.util.reporter.SuppressingReporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.aya.tyck.tycker.TeleTycker.loadTele;

public record StmtTycker(
  @NotNull SuppressingReporter reporter, @NotNull ModulePath fileModule,
  @NotNull ShapeFactory shapeFactory, @NotNull PrimFactory primFactory
) implements Problematic {
  private @NotNull ExprTycker mkTycker() {
    return new ExprTycker(new TyckState(shapeFactory, primFactory), reporter, fileModule);
  }
  public StmtTycker(
    @NotNull Reporter reporter, @NotNull ModulePath fileModule,
    @NotNull ShapeFactory shapeFactory, @NotNull PrimFactory primFactory
  ) {
    this(new SuppressingReporter(reporter), fileModule, shapeFactory, primFactory);
  }
  public void suppress(@NotNull Decl decl) {
    decl.pragmaInfo.suppressWarn.args().forEach(suppress -> {
      switch (suppress.data()) {
        case MostGeneralSolution -> reporter.suppress(MetaVarError.DidSomethingBad.class);
        case UnimportedCon -> reporter.suppress(PatternProblem.UnimportedConName.class);
        case UnreachableClause -> {
          reporter.suppress(ClausesProblem.FMDomination.class);
          reporter.suppress(ClausesProblem.Domination.class);
        }
      }
    });
  }
  public @Nullable TyckDef check(@NotNull Decl predecl) {
    ExprTycker tycker = null;
    if (predecl instanceof TeleDecl decl) {
      if (decl.ref().signature == null) tycker = checkHeader(decl);
    }

    suppress(predecl);
    var core = switch (predecl) {
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
            var expectedType = signature.result().instTeleVar(teleVars.view());
            var result = tycker.inherit(expr, expectedType).wellTyped();
            tycker.solveMetas();
            var zonker = new Finalizer.Zonk<>(tycker);
            var resultTerm = zonker.zonk(result).bindTele(teleVars.view());
            fnRef.signature = fnRef.signature.descent(zonker::zonk);
            yield factory.apply(Either.left(resultTerm));
          }
          case FnBody.BlockBody body -> {
            var clauses = body.clauses();
            var elims = body.elims();
            assert elims != null;

            var signature = fnRef.signature;
            // we do not load signature here, so we need a fresh ExprTycker
            tycker = mkTycker();
            var userTeleSize = fnDecl.telescope.size();
            var userTele = signature.params().take(userTeleSize);
            var pusheenTele = signature.params().drop(userTeleSize);
            var clauseTycker = new ClauseTycker.Worker(new ClauseTycker(tycker),
              userTele, new DepTypeTerm.Unpi(pusheenTele, signature.result()),
              teleVars, elims, clauses);

            var orderIndependent = fnDecl.modifiers.contains(Modifier.Overlap);
            FnDef def;
            ClauseTycker.TyckResult patResult;
            if (orderIndependent) {
              // Order-independent.
              patResult = clauseTycker.checkNoClassify();
              def = factory.apply(Either.right(patResult.wellTyped()));
              if (!patResult.hasLhsError()) {
                var rawParams = signature.params();
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
      // see checkHeader, if the tycking headers of these things fail,
      // we might have null cores, which is why the return type is nullable
      case DataCon _, PrimDecl _, ClassMember _ -> predecl.ref().core;
      case ClassDecl clazz -> {
        for (var member : clazz.members) checkHeader(member);
        // TODO: store signature here?
        yield new ClassDef(clazz.ref, clazz.members.map(member -> member.ref.core));
      }
      case DataDecl data -> {
        assert data.ref.signature != null;
        for (var kon : data.body.clauses) checkHeader(kon);
        yield new DataDef(data.ref, data.body.clauses.map(kon -> kon.ref.core));
      }
    };
    reporter.clearSuppress();
    return core;
  }

  public ExprTycker checkHeader(@NotNull TeleDecl decl) {
    suppress(decl);
    var tycker = mkTycker();
    switch (decl) {
      case DataCon con -> checkKitsune(con, tycker);
      case PrimDecl prim -> checkPrim(tycker, prim);
      case ClassMember member -> checkMember(member, tycker);
      case DataDecl data -> {
        var teleTycker = new TeleTycker.Default(tycker);
        var result = data.result;
        if (result == null) result = new WithPos<>(data.sourcePos(), new Expr.Sort(SortKind.Type, 0));
        var signature = teleTycker.checkSignature(data.telescope, result);
        tycker.solveMetas();
        var zonker = new Finalizer.Zonk<>(tycker);
        signature = signature.descent(zonker::zonk);
        var sort = SortTerm.Type0;
        if (signature.result() instanceof SortTerm userSort) sort = userSort;
        else fail(BadTypeError.doNotLike(tycker.state, result, signature.result(),
          _ -> Doc.plain("universe")));
        data.ref.signature = new Signature(new AbstractTele.Locns(signature.params(), sort), signature.pos());
      }
      case FnDecl fn -> {
        var teleTycker = new TeleTycker.Default(tycker);
        var result = fn.result;
        assert result != null; // See AyaProducer
        var fnRef = fn.ref;
        fnRef.signature = teleTycker.checkSignature(fn.telescope, result);

        // For ExprBody, they will be zonked later
        if (fn.body instanceof FnBody.BlockBody body) {
          tycker.solveMetas();
          var zonker = new Finalizer.Zonk<>(tycker);
          // Pusheen must be in the header, because once we have the header,
          // there will be defcalls to it,
          fnRef.signature = fnRef.signature.descent(zonker::zonk).pusheen(tycker::whnf);
          if (fnRef.signature.params().isEmpty() && body.clauses().isEmpty())
            fail(new NobodyError(decl.sourcePos(), fn.ref));
        }
      }
    }
    reporter.clearSuppress();
    return tycker;
  }
  private void checkMember(@NotNull ClassMember member, @NotNull ExprTycker tycker) {
    if (member.ref.core != null) return;
    var classRef = member.classRef;
    var self = classRef.concrete.self;
    tycker.state.classThis.push(self);
    var classCall = new ClassCall(new ClassDef.Delegate(classRef), 0, ImmutableSeq.empty());
    tycker.localCtx().put(self, classCall);
    var teleTycker = new TeleTycker.Default(tycker);
    var result = member.result;
    assert result != null; // See AyaProducer
    var signature = teleTycker.checkSignature(member.telescope, result);
    tycker.solveMetas();
    var zonker = new Finalizer.Zonk<>(tycker);
    signature = signature.pusheen(tycker::whnf)
      .descent(zonker::zonk)
      .bindTele(
        tycker.state.classThis.pop(),
        new Param("self", classCall, false),
        classRef.concrete.sourcePos()
      );

    // self is still in the context
    var type = new Synthesizer(tycker).synth(signature.telescope().inst(ImmutableSeq.of(new FreeTerm(self))).makePi());
    if (!(type instanceof SortTerm sortType)) {
      Panic.unreachable();
    } else {
      new MemberDef(classRef, member.ref, classRef.concrete.members.indexOf(member), signature.params(), signature.result(), sortType);
      member.ref.signature = signature;
    }
  }

  /**
   * Kitsune says kon!
   *
   * @apiNote invoke this method after loading the telescope of data!
   */
  private void checkKitsune(@NotNull DataCon con, @NotNull ExprTycker tycker) {
    var ref = con.ref;
    if (ref.core != null) return;
    var dataRef = con.dataRef;
    var dataDef = new DataDef.Delegate(dataRef);
    var dataSig = dataRef.signature;
    assert dataSig != null : "the header of data should be tycked";
    // Intended to be indexed, not free
    var ownerTele = dataSig.telescope().telescope().map(Param::implicitize);
    var ownerTelePos = dataSig.pos();
    var ownerBinds = dataRef.concrete.telescope.map(Expr.Param::ref);
    // dataTele already in localCtx
    // The result that a con should be, unless it is a Path result
    var freeDataCall = new DataCall(dataDef, 0, ownerBinds.map(FreeTerm::new));

    var wellPats = ImmutableSeq.<Pat>empty();
    if (con.patterns.isNotEmpty()) {
      var resolvedElim = dataRef.concrete.body.elims();
      assert resolvedElim != null;
      // do not do coverage check
      var lhsResult = new ClauseTycker(tycker = mkTycker()).checkLhs(
        SignatureIterator.make(dataSig.params(), new DepTypeTerm.Unpi(dataSig.result()), ownerBinds, resolvedElim),
        new Pattern.Clause(con.entireSourcePos(), con.patterns, Option.none()), false, 0);
      if (lhsResult.hasError()) {
        return;
      }
      wellPats = lhsResult.clause().pats();
      tycker.setLocalCtx(lhsResult.localCtx());
      lhsResult.addLocalLet(ownerBinds, tycker);
      // Here we don't use wellPats but instead a "freePats" because we want them to be bound
      freeDataCall = new DataCall(dataDef, 0, lhsResult.freePats().map(PatToTerm::visit));

      var allTypedBinds = Pat.collectBindings(wellPats.view());
      ownerBinds = lhsResult.allBinds();
      TeleTycker.bindTele(ownerBinds, allTypedBinds);
      ownerTelePos = ownerBinds.map(LocalVar::definition);
      ownerTele = allTypedBinds.map(Param::implicitize);
      if (wellPats.allMatch(pat -> pat instanceof Pat.Bind))
        wellPats = ImmutableSeq.empty();
    } else {
      loadTele(ownerBinds.view(), dataSig, tycker);
    }

    var teleTycker = new TeleTycker.Con(tycker, (SortTerm) dataSig.result());
    var selfTele = teleTycker.checkTele(con.telescope);
    var selfTelePos = con.telescope.map(Expr.Param::sourcePos);
    var selfBinds = con.teleVars();

    var conTy = con.result;
    EqTerm boundaries = null;
    if (conTy != null) {
      var pusheenResult = DepTypeTerm.unpi(DTKind.Pi, tycker.ty(conTy), tycker::whnf);

      selfTele = selfTele.appendedAll(pusheenResult.params().zip(pusheenResult.names(),
        (param, name) -> new Param(name.name(), param, true)));
      selfTelePos = selfTelePos.appendedAll(ImmutableSeq.fill(pusheenResult.params().size(), conTy.sourcePos()));

      selfBinds = selfBinds.appendedAll(pusheenResult.names());
      var tyResult = tycker.whnf(pusheenResult.body());
      if (tyResult instanceof EqTerm eq) {
        var state = tycker.state;
        var fresh = new FreeTerm("i");
        tycker.unifyTermReported(eq.appA(fresh), freeDataCall, null, conTy.sourcePos(),
          cmp -> new UnifyError.ConReturn(con, cmp, new UnifyInfo(state)));

        selfTele = selfTele.appended(new Param("i", DimTyTerm.INSTANCE, true));
        selfTelePos = selfTelePos.appended(conTy.sourcePos());

        selfBinds = selfBinds.appended(fresh.name());
        boundaries = eq;
      } else {
        var state = tycker.state;
        tycker.unifyTermReported(tyResult, freeDataCall, null, conTy.sourcePos(), cmp ->
          new UnifyError.ConReturn(con, cmp, new UnifyInfo(state)));
      }
    }
    tycker.solveMetas();

    // the result will refer to the telescope of con if it has patterns,
    // the path result may also refer to it, so we need to bind both
    var zonker = new Finalizer.Zonk<>(tycker);
    var boundDataCall = (DataCall) zonker.zonk(freeDataCall).bindTele(selfBinds);
    if (boundaries != null) boundaries = (EqTerm) zonker.zonk(boundaries).bindTele(selfBinds);
    var boundariesWithDummy = boundaries != null ? boundaries : ErrorTerm.DUMMY;
    var wholeSig = new AbstractTele.Locns(zonker.zonk(selfTele), new TupTerm(
      // This is a silly hack that allows two terms to appear in the result of a Signature
      // I considered using `AppTerm` but that is more disgraceful
      boundDataCall, boundariesWithDummy))
      .bindTele(ownerBinds.zip(ownerTele).view());
    var wholeSigResult = (TupTerm) wholeSig.result();
    boundDataCall = (DataCall) wholeSigResult.lhs();
    if (boundaries != null) boundaries = (EqTerm) wholeSigResult.rhs();

    // The signature of con should be full (the same as [konCore.telescope()])
    ref.signature = new Signature(new AbstractTele.Locns(wholeSig.telescope(), boundDataCall),
      ownerTelePos.appendedAll(selfTelePos));
    new ConDef(dataDef, ref, wellPats, boundaries,
      ownerTele,
      wholeSig.telescope().drop(ownerTele.size()),
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
      primRef.signature = new Signature(TyckDef.defSignature(core), ImmutableSeq.fill(core.telescope().size(), pos));
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
      DepTypeTerm.makePi(tele.params().view().map(Param::type), tele.result()),
      // No checks, slightly faster than TeleDef.defType
      DepTypeTerm.makePi(core.telescope.view().map(Param::type), core.result),
      null, prim.entireSourcePos(),
      msg -> new PrimError.BadSignature(prim, msg, new UnifyInfo(tycker.state)));
    var zonker = new Finalizer.Zonk<>(tycker);
    primRef.signature = tele.descent(zonker::zonk);
    tycker.solveMetas();
    tycker.setLocalCtx(new MapLocalCtx());
  }
}
