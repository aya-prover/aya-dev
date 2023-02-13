// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.unify;

import kala.collection.SeqLike;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableHashMap;
import kala.collection.mutable.MutableMap;
import kala.tuple.Tuple;
import org.aya.concrete.stmt.decl.TeleDecl;
import org.aya.core.def.Def;
import org.aya.core.def.PrimDef;
import org.aya.core.term.*;
import org.aya.core.visitor.AyaRestrSimplifier;
import org.aya.core.visitor.Subst;
import org.aya.generic.SortKind;
import org.aya.generic.util.InternalException;
import org.aya.guest0x0.cubical.CofThy;
import org.aya.guest0x0.cubical.Partial;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.ref.AnyVar;
import org.aya.ref.DefVar;
import org.aya.ref.LocalVar;
import org.aya.tyck.env.LocalCtx;
import org.aya.tyck.error.LevelError;
import org.aya.tyck.trace.Trace;
import org.aya.tyck.tycker.MockTycker;
import org.aya.tyck.tycker.TyckState;
import org.aya.util.Arg;
import org.aya.util.Ordering;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Bidirectional unification of terms, with abstract {@link TermComparator#solveMeta}.
 * This class is not called <code>Comparator</code> because there is already {@link java.util.Comparator}.
 *
 * @see Unifier Pattern unification implementation
 * @see #compareUntyped(Term, Term, Sub, Sub) the "synthesize" direction
 * @see #compare(Term, Term, Sub, Sub, Term) the "inherit" direction
 */
public sealed abstract class TermComparator extends MockTycker permits Unifier {
  protected final @NotNull SourcePos pos;
  protected final @NotNull Ordering cmp;
  private FailureData failure;

  public TermComparator(@Nullable Trace.Builder traceBuilder, @NotNull TyckState state, @NotNull Reporter reporter, @NotNull SourcePos pos, @NotNull Ordering cmp, @NotNull LocalCtx ctx) {
    super(reporter, traceBuilder, state, ctx);
    this.pos = pos;
    this.cmp = cmp;
  }

  private static boolean isCall(@NotNull Term term) {
    return term instanceof FnCall || term instanceof ConCall || term instanceof PrimCall;
  }

  public static <E> E withIntervals(
    @NotNull ImmutableSeq<LocalVar> l, @NotNull ImmutableSeq<LocalVar> r, Sub lr, Sub rl,
    @NotNull ImmutableSeq<LocalVar> tyVars,
    @NotNull BiFunction<Subst, Subst, E> supplier
  ) {
    assert l.sizeEquals(r);
    var lSubst = new Subst();
    var rSubst = new Subst();
    for (var conv : l.view().zip3(r, tyVars)) {
      lr.map.put(conv.component3(), new RefTerm(conv.component2()));
      rl.map.put(conv.component3(), new RefTerm(conv.component1()));
      lSubst.addDirectly(conv.component1(), new RefTerm(conv.component3()));
      rSubst.addDirectly(conv.component2(), new RefTerm(conv.component3()));
    }
    var res = supplier.apply(lSubst, rSubst);
    lr.map.removeAll(tyVars);
    rl.map.removeAll(tyVars);
    return res;
  }

  private static boolean sortLt(SortTerm l, SortTerm r) {
    var lift = l.lift();
    var rift = r.lift();
    return switch (l.kind()) {
      case Type -> switch (r.kind()) {
        case Type, Set -> lift <= rift;
        case default -> false;
      };
      case ISet -> switch (r.kind()) {
        case ISet, Set -> true;
        case default -> false;
      };
      case Prop -> r.kind() == SortKind.Prop;
      case Set -> r.kind() == SortKind.Set && lift <= rift;
    };
  }

  public @NotNull FailureData getFailure() {
    assert failure != null;
    return failure.map(t -> t.freezeHoles(state));
  }

  private void traceEntrance(@NotNull Trace trace) {
    tracing(builder -> builder.shift(trace));
  }

  private void traceExit() {
    tracing(Trace.Builder::reduce);
  }

  public boolean compare(@NotNull Term lhs, @NotNull Term rhs, @Nullable Term type) {
    return compare(lhs, rhs, new Sub(), new Sub(), type);
  }

  protected final boolean compare(Term lhs, Term rhs, Sub lr, Sub rl, @Nullable Term type) {
    if (lhs == rhs) return true;
    if (compareApprox(lhs, rhs, lr, rl) != null) return true;
    lhs = whnf(lhs);
    rhs = whnf(rhs);
    if (compareApprox(lhs, rhs, lr, rl) != null) return true;
    if (rhs instanceof MetaTerm rMeta) {
      // In case we're comparing two metas with one isType and the other has a type,
      // prefer solving the isType one to the typed one.
      if (lhs instanceof MetaTerm lMeta && lMeta.ref().info.result() == null)
        return solveMeta(lMeta, rMeta, lr, rl, type) != null;
      return solveMeta(rMeta, lhs, rl, lr, type) != null;
    }
    // ^ Beware of the order!!
    if (lhs instanceof MetaTerm lMeta) {
      return solveMeta(lMeta, rhs, lr, rl, type) != null;
    } else if (type == null) {
      return compareUntyped(lhs, rhs, lr, rl) != null;
    }
    if (lhs instanceof ErrorTerm || rhs instanceof ErrorTerm) return true;
    var result = doCompareTyped(whnf(type), lhs, rhs, lr, rl);
    if (!result && failure == null) failure = new FailureData(lhs, rhs);
    return result;
  }

  @Nullable protected Term compareUntyped(@NotNull Term lhs, @NotNull Term rhs, Sub lr, Sub rl) {
    traceEntrance(new Trace.UnifyT(lhs.freezeHoles(state),
      rhs.freezeHoles(state), pos));
    // lhs & rhs will both be WHNF if either is not a potentially reducible call
    if (isCall(lhs) || isCall(rhs)) {
      var ty = compareApprox(lhs, rhs, lr, rl);
      if (ty == null) ty = doCompareUntyped(lhs, rhs, lr, rl);
      if (ty != null) return whnf(ty);
    }
    lhs = whnf(lhs);
    rhs = whnf(rhs);
    Term x;
    if (rhs instanceof MetaLitTerm || rhs instanceof MetaTerm)
      x = doCompareUntyped(rhs, lhs, rl, lr);
    else x = doCompareUntyped(lhs, rhs, lr, rl);
    traceExit();
    if (x != null) return whnf(x);
    if (failure == null) failure = new FailureData(lhs, rhs);
    return null;
  }

  private boolean compareWHNF(Term lhs, Term preRhs, Sub lr, Sub rl, @NotNull Term type) {
    var whnf = whnf(lhs);
    var rhsWhnf = whnf(preRhs);
    if (Objects.equals(whnf, lhs) && Objects.equals(rhsWhnf, preRhs)) return false;
    return compare(whnf, rhsWhnf, lr, rl, type);
  }

  private @Nullable Term compareApprox(@NotNull Term preLhs, @NotNull Term preRhs, Sub lr, Sub rl) {
    return switch (preLhs) {
      case FnCall lhs when preRhs instanceof FnCall rhs ->
        lhs.ref() != rhs.ref() ? null : visitCall(lhs, rhs, lr, rl, lhs.ref(), lhs.ulift());
      case ConCall lhs when preRhs instanceof ConCall rhs ->
        lhs.ref() != rhs.ref() ? null : lossyUnifyCon(lhs, rhs, lr, rl);
      case PrimCall lhs when preRhs instanceof PrimCall rhs ->
        lhs.ref() != rhs.ref() ? null : visitCall(lhs, rhs, lr, rl, lhs.ref(), lhs.ulift());
      default -> null;
    };
  }

  private <T> T checkParam(
    Term.Param l, Term.Param r, Subst lsub, Subst rsub,
    Sub lr, Sub rl, Supplier<T> fail, BiFunction<Subst, Subst, T> success
  ) {
    if (l.explicit() != r.explicit()) return fail.get();
    var lTy = l.type().subst(lsub);
    if (!compare(lTy, r.type().subst(rsub), lr, rl, null)) return fail.get();
    // Do not substitute when one side is ignored
    if (l.ref() == LocalVar.IGNORED || r.ref() == LocalVar.IGNORED) {
      return success.apply(lsub, rsub);
    } else {
      var i = new LocalVar(l.ref().name() + r.ref().name());
      rl.map.put(i, l.toTerm());
      lr.map.put(i, r.toTerm());
      var term = new RefTerm(i);
      lsub.addDirectly(l.ref(), term);
      rsub.addDirectly(r.ref(), term);
      var result = ctx.with(i, lTy, () -> success.apply(lsub, rsub));
      rl.map.remove(i);
      lr.map.remove(i);
      return result;
    }
  }

  private <T> T checkParams(
    SeqView<Term.Param> l, SeqView<Term.Param> r, Subst lsub, Subst rsub,
    Sub lr, Sub rl, Supplier<T> fail, BiFunction<Subst, Subst, T> success
  ) {
    if (!l.sizeEquals(r)) return fail.get();
    if (l.isEmpty()) return success.apply(lsub, rsub);
    return checkParam(l.first(), r.first(), lsub, rsub, lr, rl, fail, (ls, rs) ->
      checkParams(l.drop(1), r.drop(1), ls, rs, lr, rl, fail, success));
  }

  private boolean visitArgs(SeqLike<Arg<Term>> l, SeqLike<Arg<Term>> r, Sub lr, Sub rl, SeqLike<Term.Param> params) {
    return visitLists(l.view().map(Arg::term), r.view().map(Arg::term), lr, rl, params);
  }

  private boolean visitLists(SeqView<Term> l, SeqView<Term> r, Sub lr, Sub rl, @NotNull SeqLike<Term.Param> types) {
    assert l.sizeEquals(r);
    assert r.sizeEquals(types);
    var typesSubst = types.view();
    var lu = l.toImmutableSeq();
    var ru = r.toImmutableSeq();
    for (int i = 0; lu.sizeGreaterThan(i); i++) {
      var li = lu.get(i);
      var head = typesSubst.first();
      if (!compare(li, ru.get(i), lr, rl, head.type())) return false;
      typesSubst = typesSubst.drop(1).map(type -> type.subst(head.ref(), li));
    }
    return true;
  }

  private @Nullable Term visitCall(
    @NotNull Callable lhs, @NotNull Callable rhs, Sub lr, Sub rl,
    @NotNull DefVar<? extends Def, ? extends TeleDecl<?>> lhsRef, int ulift
  ) {
    var retType = synthesizer().press(lhs);
    if (synthesizer().tryPress(retType) instanceof SortTerm sort && sort.isProp()) return retType;
    // Lossy comparison
    if (visitArgs(lhs.args(), rhs.args(), lr, rl,
      Term.Param.subst(Def.defTele(lhsRef), ulift))) return retType;
    if (compareWHNF(lhs, rhs, lr, rl, retType)) return retType;
    else return null;
  }

  /** TODO: Revise when JDK 20 is released. */
  private record Pair(Term lhs, Term rhs) {}

  private boolean doCompareTyped(@NotNull Term type, @NotNull Term lhs, @NotNull Term rhs, Sub lr, Sub rl) {
    // Skip tracing, because too easy.
    // Note that it looks tempting to apply some unification here, but it is not correct:
    // If ?x =_A y where A : Prop, then it may not be the case that ?x is y!
    // I think Arend has probably made such a mistake before, but they removed this feature anyway.
    if (synthesizer().tryPress(type) instanceof SortTerm sort && sort.isProp()) return true;
    traceEntrance(new Trace.UnifyT(lhs.freezeHoles(state), rhs.freezeHoles(state),
      pos, type.freezeHoles(state)));
    var ret = switch (type) {
      default -> compareUntyped(lhs, rhs, lr, rl) != null;
      case ClassCall type1 -> {
        var fieldSigs = type1.ref().core.fields;
        var paramSubst = ImmutableMap.from(Def.defTele(type1.ref()).zipView(type1.args()).map(x ->
          Tuple.of(x.component1().ref(), x.component2().term())));
        var fieldSubst = new Subst(MutableHashMap.create());
        for (var fieldSig : fieldSigs) {
          var dummyVars = fieldSig.selfTele.map(par -> par.ref().rename());
          var dummy = dummyVars.zip(fieldSig.selfTele).map(vpa ->
            new Arg<Term>(new RefTerm(vpa.component1()), vpa.component2().explicit()));
          var l = new FieldTerm(lhs, fieldSig.ref(), type1.args(), dummy);
          var r = new FieldTerm(rhs, fieldSig.ref(), type1.args(), dummy);
          fieldSubst.add(fieldSig.ref(), l);
          if (!compare(l, r, lr, rl, fieldSig.result().subst(paramSubst).subst(fieldSubst))) yield false;
        }
        yield true;
      }
      case LamTerm $ -> throw new InternalException("LamTerm is never type");
      case ConCall $ -> throw new InternalException("ConCall is never type");
      case TupTerm $ -> throw new InternalException("TupTerm is never type");
      case NewTerm $ -> throw new InternalException("NewTerm is never type");
      case ErrorTerm $ -> true;
      case SigmaTerm(var paramsSeq) -> {
        var params = paramsSeq.view();
        for (int i = 1, size = paramsSeq.size(); i <= size; i++) {
          var l = ProjTerm.proj(lhs, i);
          var currentParam = params.first();
          ctx.put(currentParam);
          if (!compare(l, ProjTerm.proj(rhs, i), lr, rl, currentParam.type())) yield false;
          params = params.drop(1).map(x -> x.subst(currentParam.ref(), l));
        }
        ctx.remove(paramsSeq.view().map(Term.Param::ref));
        yield true;
      }
      case PiTerm pi -> ctx.with(pi.param(), () -> switch (new Pair(lhs, rhs)) {
        case Pair(LamTerm(var lp, var lb), LamTerm(var rp, var rb)) -> {
          var ref = pi.param().ref();
          if (ref == LocalVar.IGNORED) ref = new LocalVar(lp.ref().name() + rp.ref().name());
          lr.map.put(ref, rp.toTerm());
          rl.map.put(ref, lp.toTerm());
          var piParam = new RefTerm(ref);
          var res = compare(lb.subst(lp.ref(), piParam), rb.subst(rp.ref(), piParam), lr, rl, pi.body());
          lr.map.remove(ref);
          rl.map.remove(ref);
          yield res;
        }
        case Pair(var $, LamTerm rambda) -> compareLambdaBody(rambda, lhs, rl, lr, pi);
        case Pair(LamTerm lambda, var $) -> compareLambdaBody(lambda, rhs, lr, rl, pi);
        // Question: do we need a unification for Pi.body?
        case default -> compare(lhs, rhs, lr, rl, null);
      });
      // In this case, both sides have the same type (I hope)
      case PathTerm cube -> ctx.withIntervals(cube.params().view(), () -> {
        if (lhs instanceof PLamTerm lambda) {
          assert lambda.params().sizeEquals(cube.params());
          if (rhs instanceof PLamTerm(var rparams, var rbody)) {
            assert rparams.sizeEquals(cube.params());
            withIntervals(lambda.params(), rparams, lr, rl, cube.params(), (lsub, rsub) ->
              compare(lambda.body().subst(lsub), rbody.subst(rsub), lr, rl, cube.type()));
          }
          return comparePathLamBody(lambda, rhs, lr, rl, cube);
        }
        if (rhs instanceof PLamTerm rambda) {
          assert rambda.params().sizeEquals(cube.params());
          return comparePathLamBody(rambda, lhs, rl, lr, cube);
        }
        // Question: do we need a unification for Pi.body?
        return compare(lhs, rhs, lr, rl, null);
      });
      case PartialTyTerm ty -> {
        if (lhs instanceof PartialTerm lel && rhs instanceof PartialTerm rel)
          yield comparePartial(lel, rel, ty, lr, rl);
        else yield false;
      }
      case PrimCall prim when prim.id() == PrimDef.ID.SUB -> {
        // See PrimDef.Factory.Initializer.sub
        var A = prim.args().get(0).term();
        if (new Pair(lhs, rhs) instanceof Pair(InTerm(var lPhi, var lU), InTerm(var rPhi, var rU))) {
          if (!compare(lPhi, rPhi, lr, rl, IntervalTerm.INSTANCE)) yield false;
          yield compare(lU, rU, lr, rl, A);
        } else yield compare(lhs, rhs, lr, rl, A);
      }
    };
    traceExit();
    return ret;
  }

  private boolean compareLambdaBody(LamTerm lambda, Term rhs, Sub lr, Sub rl, PiTerm pi) {
    var lhsArg = lambda.param().toArg();
    rl.map.put(pi.param().ref(), lambda.param().toTerm());
    var result = ctx.with(lambda.param().ref(), pi.param().type(), () ->
      compare(AppTerm.make(lambda, lhsArg), AppTerm.make(rhs, lhsArg), lr, rl, pi.body()));
    rl.map.remove(pi.param().ref());
    return result;
  }

  private boolean comparePathLamBody(PLamTerm lambda, Term rhs, Sub lr, Sub rl, PathTerm cube) {
    cube.params().forEachWith(lambda.params(), (a, b) -> rl.map.put(a, new RefTerm(b)));
    var result = ctx.withIntervals(lambda.params().view(), () ->
      compare(cube.applyDimsTo(lambda), cube.applyDimsTo(rhs), lr, rl, cube.type()));
    rl.map.removeAll(cube.params());
    return result;
  }

  private boolean comparePartial(
    @NotNull PartialTerm lhs, @NotNull PartialTerm rhs,
    @NotNull PartialTyTerm type, Sub lr, Sub rl
  ) {
    record P(Partial<Term> l, Partial<Term> r) {}
    return switch (new P(lhs.partial(), rhs.partial())) {
      case P(Partial.Const<Term>(var ll), Partial.Const<Term>(var rr)) -> compare(ll, rr, lr, rl, type.type());
      case P(Partial.Split<Term> ll, Partial.Split<Term> rr) -> CofThy.conv(type.restr(), new Subst(),
        subst -> compare(lhs.subst(subst), rhs.subst(subst), lr, rl, type.subst(subst)));
      default -> false;
    };
  }

  private boolean compareCube(@NotNull PathTerm lhs, @NotNull PathTerm rhs, Sub lr, Sub rl) {
    var tyVars = rhs.params();
    return ctx.withIntervals(tyVars.view(), () -> withIntervals(lhs.params(), rhs.params(), lr, rl, tyVars, (lsub, rsub) -> {
      var lPar = (PartialTerm) new PartialTerm(lhs.partial(), lhs.type()).subst(lsub);
      var rPar = (PartialTerm) new PartialTerm(rhs.partial(), rhs.type()).subst(rsub);
      var lType = new PartialTyTerm(lPar.rhsType(), lPar.partial().restr());
      var rType = new PartialTyTerm(rPar.rhsType(), rPar.partial().restr());
      if (!compare(lType, rType, lr, rl, null)) return false;
      return comparePartial(lPar, rPar, lType, lr, rl);
    }));
  }

  /**
   * @implNote Do not need to compute result type precisely because unification won't need this info
   * (written by re-xyr)
   * @see #doCompareUntyped
   */
  private boolean doCompareType(@NotNull Formation preLhs, @NotNull Term preRhs, Sub lr, Sub rl) {
    if (preLhs.getClass() != preRhs.getClass()) return false;
    record Pair(Formation lhs, Formation rhs) {
    }
    return switch (new Pair(preLhs, (Formation) preRhs)) {
      default -> throw noRules(preLhs);
      case Pair(DataCall lhs, DataCall rhs) -> {
        if (lhs.ref() != rhs.ref()) yield false;
        yield visitArgs(lhs.args(), rhs.args(), lr, rl, Term.Param.subst(Def.defTele(lhs.ref()), lhs.ulift()));
      }
      case Pair(ClassCall lhs, ClassCall rhs) -> {
        if (lhs.ref() != rhs.ref()) yield false;
        yield visitArgs(lhs.args(), rhs.args(), lr, rl, Term.Param.subst(Def.defTele(lhs.ref()), lhs.ulift()));
      }
      case Pair(PiTerm(var lParam, var lBody), PiTerm(var rParam, var rBody)) ->
        checkParam(lParam, rParam, new Subst(), new Subst(), lr, rl, () -> false,
          (lsub, rsub) -> compare(lBody.subst(lsub), rBody.subst(rsub), lr, rl, null));
      case Pair(SigmaTerm(var lParams), SigmaTerm(var rParams)) -> checkParams(lParams.view(), rParams.view(),
        new Subst(), new Subst(), lr, rl, () -> false, (lsub, rsub) -> true);
      case Pair(SortTerm lhs, SortTerm rhs) -> compareSort(lhs, rhs);
      case Pair(PartialTyTerm(var lTy, var lR), PartialTyTerm(var rTy, var rR)) ->
        compare(lTy, rTy, lr, rl, null) && compareRestr(lR, rR);
      case Pair(PathTerm lCube, PathTerm rCube) -> compareCube(lCube, rCube, lr, rl);
      case Pair(IntervalTerm lhs, IntervalTerm rhs) -> true;
    };
  }

  private Term doCompareUntyped(@NotNull Term preLhs, @NotNull Term preRhs, Sub lr, Sub rl) {
    if (preLhs instanceof Formation lhs) return doCompareType(lhs, preRhs, lr, rl) ? SortTerm.Type0 : null;
    return switch (preLhs) {
      default -> throw noRules(preLhs);
      case ErrorTerm term -> ErrorTerm.typeOf(term);
      case MetaPatTerm(var lhsRef) -> {
        if (preRhs instanceof MetaPatTerm(var rRef) && lhsRef == rRef) yield lhsRef.type();
        else yield null;
      }
      case RefTerm(var lhs) -> preRhs instanceof RefTerm(var rhs) && lhs == rhs ? ctx.get(lhs) : null;
      case AppTerm(var lOf, var lArg) -> {
        if (!(preRhs instanceof AppTerm(var rOf, var rArg))) yield null;
        var preFnType = compareUntyped(lOf, rOf, lr, rl);
        if (!(preFnType instanceof PiTerm fnType)) yield null;
        if (!compare(lArg.term(), rArg.term(), lr, rl, fnType.param().type())) yield null;
        yield fnType.substBody(lArg.term());
      }
      case PAppTerm lhs -> {
        if (!(preRhs instanceof PAppTerm rhs)) yield null;
        var prePathType = compareUntyped(lhs.of(), rhs.of(), lr, rl);
        if (!(prePathType instanceof PathTerm cube)) yield null;
        var happy = lhs.args().allMatchWith(rhs.args(), (l, r) ->
          compare(l.term(), r.term(), lr, rl, null));
        yield happy ? cube.type() : null;
      }
      case ProjTerm lhs -> {
        if (!(preRhs instanceof ProjTerm(var rof, var rix))) yield null;
        var preTupType = compareUntyped(lhs.of(), rof, lr, rl);
        if (!(preTupType instanceof SigmaTerm(var tele))) yield null;
        if (lhs.ix() != rix) yield null;
        var params = tele.view();
        var subst = new Subst(MutableMap.create());
        for (int i = 1; i < lhs.ix(); i++) {
          var l = ProjTerm.proj(lhs, i);
          var currentParam = params.first();
          subst.add(currentParam.ref(), l);
          params = params.drop(1);
        }
        if (params.isNotEmpty()) yield params.first().subst(subst).type();
        yield params.last().subst(subst).type();
      }
      case FormulaTerm lhs -> {
        if (!(preRhs instanceof FormulaTerm rhs)) yield null;
        if (compareRestr(AyaRestrSimplifier.INSTANCE.isOne(lhs), AyaRestrSimplifier.INSTANCE.isOne(rhs)))
          yield IntervalTerm.INSTANCE;
        else yield null;
      }
      // See compareApprox for why we don't compare these
      case FnCall lhs -> null;
      case CoeTerm lhs -> {
        if (!(preRhs instanceof CoeTerm rhs)) yield null;
        if (!compareRestr(lhs.restr(), rhs.restr())) yield null;
        yield compare(lhs.type(), rhs.type(), lr, rl, PrimDef.intervalToType()) ?
          PrimDef.familyLeftToRight(lhs.type()) : null;
      }
      case ConCall lhs -> switch (preRhs) {
        case ConCall rhs -> {
          var lef = lhs.ref();
          yield lef != rhs.ref() ? null : lossyUnifyCon(lhs, rhs, lr, rl);
        }
        case IntegerTerm rhs -> compareUntyped(lhs, rhs.constructorForm(), lr, rl);
        case ListTerm rhs -> compareUntyped(lhs, rhs.constructorForm(), lr, rl);
        default -> null;
      };
      case PrimCall lhs -> null;
      case FieldTerm lhs -> {
        if (!(preRhs instanceof FieldTerm rhs)) yield null;
        var preStructType = compareUntyped(lhs.of(), rhs.of(), lr, rl);
        if (!(preStructType instanceof ClassCall)) yield null;
        if (lhs.ref() != rhs.ref()) yield null;
        yield Def.defResult(lhs.ref());
      }
      case IntegerTerm lhs -> switch (preRhs) {
        case IntegerTerm rhs -> {
          if (!lhs.compareShape(this, rhs)) yield null;
          if (!lhs.compareUntyped(rhs)) yield null;
          yield lhs.type(); // compareShape implies lhs.type() = rhs.type()
        }
        case ConCall rhs -> compareUntyped(lhs.constructorForm(), rhs, lr, rl);
        default -> null;
      };
      // We expect to only compare the elimination "outS" here
      case OutTerm(var lPhi, var pal, var lU) -> {
        if (!(preRhs instanceof OutTerm(var rPhi, var par, var rU))) yield null;
        if (!compare(lPhi, rPhi, lr, rl, IntervalTerm.INSTANCE)) yield null;
        var innerTy = compareUntyped(lU, rU, lr, rl);
        if (innerTy == null) yield null;
        var prim = (PrimCall) whnf(innerTy);
        yield prim.args().get(0).term();
      }
      case ListTerm lhs -> switch (preRhs) {
        case ListTerm rhs -> {
          if (!lhs.compareShape(this, rhs)) yield null;
          if (!lhs.compareUntyped(rhs, (l, r) -> compare(l, r, lr, rl, null))) yield null;
          yield lhs.type();
        }
        case ConCall rhs -> compareUntyped(lhs.constructorForm(), rhs, lr, rl);
        default -> null;
      };
      case MetaLitTerm lhs -> {
        if (preRhs instanceof IntegerTerm rhs) {
          yield compareMetaLitWithLit(lhs, rhs.repr(), rhs.type(), lr, rl);
        }
        if (preRhs instanceof ListTerm rhs)
          yield compareMetaLitWithLit(lhs, rhs.repr(), rhs.type(), lr, rl);
        if (preRhs instanceof ConCall rhs)
          throw new UnsupportedOperationException("TODO (I have no time to implement this)");
        if (preRhs instanceof MetaLitTerm rhs)
          yield compareMetaLitWithLit(lhs, rhs.repr(), rhs.type(), lr, rl);
        yield null;
      }
      case MetaTerm lhs -> solveMeta(lhs, preRhs, lr, rl, null);
    };
  }

  private @Nullable Term compareMetaLitWithLit(
    @NotNull MetaLitTerm lhs, Object repr, @NotNull Term rhsType,
    Sub lr, Sub rl
  ) {
    if (!Objects.equals(lhs.repr(), repr)) return null;
    if (compare(lhs.type(), rhsType, lr, rl, null)) return lhs.type();
    return null;
  }

  private static @NotNull InternalException noRules(@NotNull Term preLhs) {
    return new InternalException(preLhs.getClass() + ": " + preLhs.toDoc(AyaPrettierOptions.debug()).debugRender());
  }

  /**
   * {@link ConCall} may reduce according to conditions, so this comparison is a lossy one.
   * If called from {@link #doCompareUntyped} then probably not so lossy.
   */
  private @Nullable Term lossyUnifyCon(ConCall lhs, ConCall rhs, Sub lr, Sub rl) {
    var retType = synthesizer().press(lhs);
    var dataRef = lhs.ref().core.dataRef;
    if (Def.defResult(dataRef).isProp()) return retType;
    var dataAlgs = lhs.head().dataArgs();
    if (!visitArgs(dataAlgs, rhs.head().dataArgs(), lr, rl,
      Term.Param.subst(Def.defTele(dataRef), lhs.ulift()))) return null;
    if (visitArgs(lhs.conArgs(), rhs.conArgs(), lr, rl,
      Term.Param.subst(lhs.ref().core.selfTele, conOwnerSubst(lhs), lhs.ulift())))
      return retType;
    return null;
  }

  protected abstract @Nullable Term solveMeta(@NotNull MetaTerm lhs, @NotNull Term preRhs, Sub lr, Sub rl, @Nullable Term providedType);

  public boolean compareSort(SortTerm l, SortTerm r) {
    var result = switch (cmp) {
      case Gt -> sortLt(r, l);
      case Eq -> l.kind() == r.kind() && l.lift() == r.lift();
      case Lt -> sortLt(l, r);
    };
    if (!result) {
      switch (cmp) {
        case Eq -> reporter.report(new LevelError(pos, l, r, true));
        case Gt -> reporter.report(new LevelError(pos, l, r, false));
        case Lt -> reporter.report(new LevelError(pos, r, l, false));
      }
    }
    return result;
  }

  @Debug.Renderer(childrenArray = "map.toArray()", hasChildren = "!map.isEmpty()")
  public record Sub(@NotNull MutableMap<@NotNull AnyVar, @NotNull RefTerm> map) implements Cloneable {
    public Sub() {
      this(MutableMap.create());
    }

    @SuppressWarnings("MethodDoesntCallSuperMethod") public @NotNull Sub clone() {
      return new Sub(MutableMap.from(map));
    }
  }

  public record FailureData(@NotNull Term lhs, @NotNull Term rhs) {
    public @NotNull FailureData map(@NotNull UnaryOperator<Term> f) {
      return new FailureData(f.apply(lhs), f.apply(rhs));
    }
  }
}
