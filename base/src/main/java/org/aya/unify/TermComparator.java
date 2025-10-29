// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.unify;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableStack;
import org.aya.generic.Renamer;
import org.aya.generic.term.DTKind;
import org.aya.generic.term.SortKind;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.states.TyckState;
import org.aya.syntax.core.annotation.Closed;
import org.aya.syntax.core.term.*;
import org.aya.syntax.core.term.call.*;
import org.aya.syntax.core.term.marker.Formation;
import org.aya.syntax.core.term.repr.IntegerTerm;
import org.aya.syntax.core.term.repr.ListTerm;
import org.aya.syntax.core.term.repr.MetaLitTerm;
import org.aya.syntax.core.term.xtt.*;
import org.aya.syntax.ref.LocalCtx;
import org.aya.syntax.ref.LocalVar;
import org.aya.syntax.ref.MetaVar;
import org.aya.syntax.telescope.AbstractTele;
import org.aya.tyck.error.LevelError;
import org.aya.tyck.tycker.AbstractTycker;
import org.aya.tyck.tycker.Contextful;
import org.aya.util.*;
import org.aya.util.position.SourcePos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

// TODO: remove suppressing after refactor
@SuppressWarnings("Dblity")
public abstract sealed class TermComparator extends AbstractTycker permits Unifier {
  protected final @NotNull SourcePos pos;
  protected @NotNull Ordering cmp;
  private @Nullable FailureData failure = null;
  final @NotNull Renamer nameGen = new Renamer();

  /// If false, we refrain from solving meta, and return false if we encounter a non-identical meta.
  /// Used for approximate comparison.
  private boolean solveMetaForApprox = true;
  /// If false, do not try to solve metas. This is used for filtering instance candidates.
  private boolean solveMetaInstances = true;
  private final MutableStack<MutableList<TyckState.Eqn>> weWillSee = MutableStack.create();

  public void instanceFilteringMode() {
    this.solveMetaInstances = false;
  }

  public TermComparator(
    @NotNull TyckState state, @NotNull LocalCtx ctx,
    @NotNull Reporter reporter, @NotNull SourcePos pos, @NotNull Ordering cmp
  ) {
    super(state, ctx, reporter);
    nameGen.store(ctx);
    this.pos = pos;
    this.cmp = cmp;
  }

  /**
   * Trying to solve {@param meta} with {@param rhs}
   *
   * @param rhs in whnf
   */
  protected abstract @Nullable Term doSolveMeta(@NotNull MetaCall meta, @NotNull Term rhs, @Nullable Term type);

  /** The "flex-flex" case with identical meta ref */
  private @Nullable Term sameMeta(@NotNull MetaCall meta, @Nullable Term type, MetaCall rMeta) {
    if (meta.args().size() != rMeta.args().size()) return null;
    for (var i = 0; i < meta.args().size(); i++) {
      var cmpRes = compare(meta.args().get(i), rMeta.args().get(i), null);
      if (cmpRes == ThreeState.NO) {    // TODO: what about UNSURE?
        return null;
      }
    }
    if (type != null) return type;
    if (meta.ref().req() instanceof MetaVar.OfType(var ty)) return ty;
    // Honestly, this is a bit sus
    return ErrorTerm.typeOf(meta);
  }

  public @NotNull TyckState.Eqn createEqn(@NotNull MetaCall lhs, @NotNull Term rhs, @Nullable Term type) {
    return new TyckState.Eqn(lhs, rhs, type, cmp, pos, localCtx().clone());
  }

  protected @Nullable Term solveMeta(@NotNull MetaCall meta, @NotNull Term rhs, @Nullable Term type) {
    rhs = whnf(rhs);
    if (rhs instanceof MetaCall rMeta && rMeta.ref() == meta.ref())
      return sameMeta(meta, type, rMeta);

    if (solveMetaInstances && solveMetaForApprox) {
      var result = doSolveMeta(meta, rhs, type);
      if (result == null) fail(meta, rhs);
      return result;
    } else {
      weWillSee.peek().append(createEqn(meta, rhs, type));
      return type != null ? type : ErrorTerm.typeOf(meta);
    }
  }

  // region Utilities
  private void fail(@NotNull Term lhs, @NotNull Term rhs) {
    if (failure == null) {
      failure = new FailureData(lhs, rhs);
    }
  }

  private @NotNull Panic noRules(@NotNull Term term) {
    return new Panic(term.getClass() + ": " + term.toDoc(AyaPrettierOptions.debug()).debugRender());
  }
  // endregion Utilities

  /**
   * Compare arguments ONLY.
   * For lossy comparisons, when we fail, we will need to compare them again later,
   * so don't forget to reset the {@link #failure} after first failure.
   */
  private @Closed @NotNull RelDec<Term> compareApprox(@NotNull Term lhs, @NotNull Term rhs) {
    var prev = solveMetaForApprox;
    solveMetaForApprox = false;
    weWillSee.push(MutableList.create());
    var result = compareCalls(lhs, rhs);
    var weWillSeeThisTime = weWillSee.pop();
    solveMetaForApprox = prev;

    if (result.isYes()) {
      var acc = ThreeState.YES;
      for (var eqn : weWillSeeThisTime) {
        // Make sure to call `solveEqn` on a fresh Unifier to have the correct `localCtx`
        var solveRes = state.solveEqn(reporter, eqn, true);
        acc = acc.lub(solveRes);
        if (acc == ThreeState.NO) return RelDec.no();   // shortcut TODO what about UNSURE
      }

      return result.lub(acc);
    }

    return result;
  }

  private @Closed @NotNull RelDec<Term> compareCalls(@NotNull Term lhs, @NotNull Term rhs) {
    if (lhs instanceof RuleReducer.Fn fn) lhs = fn.toFnCall();
    if (rhs instanceof RuleReducer.Fn fn) rhs = fn.toFnCall();
    return switch (new Pair<>(lhs, rhs)) {
      case Pair(LetFreeTerm lFree, LetFreeTerm rFree) when lFree.name() == rFree.name() ->
        RelDec.of(lFree.definedAs().type());
      case Pair(FnCall lFn, FnCall rFn) -> compareCallApprox(lFn, rFn);
      case Pair(DataCall lFn, DataCall rFn) -> compareCallApprox(lFn, rFn);
      case Pair(PrimCall lFn, PrimCall rFn) -> compareCallApprox(lFn, rFn);
      case Pair(IntegerTerm lInt, IntegerTerm rInt) ->
        lInt.repr() == rInt.repr() ? RelDec.of(lInt.type()) : RelDec.no();
      case Pair(ConCallLike lCon, ConCallLike rCon) -> compareCallApprox(lCon, rCon);
      case Pair(MemberCall lMem, MemberCall rMem) -> {
        if (!lMem.ref().equals(rMem.ref())) yield RelDec.no();
        // TODO: type info?
        var result = compare(lMem.of(), rMem.of(), null);
        yield result.lubRelDec(() ->
          compareMany(lMem.args(), rMem.args(),
            lMem.ref().signature()
              .inst(ImmutableSeq.of(lMem.of()))
              .lift(Math.min(lMem.ulift(), rMem.ulift()))));
      }
      default -> RelDec.no();
    };
  }

  /// Compare the arguments of two callable ONLY, this method will NOT try to
  /// normalize and then compare (while the old version of Aya does).
  private RelDec.@Closed @NotNull Strict<Term> compareCallApprox(@NotNull Callable.Tele lhs, @NotNull Callable.Tele rhs) {
    if (!lhs.ref().equals(rhs.ref())) return RelDec.no();
    return compareMany(lhs.args(), rhs.args(),
      lhs.ref().signature().lift(Math.min(lhs.ulift(), rhs.ulift())));
  }

  private <R> R swapped(@NotNull Supplier<R> callback) {
    cmp = cmp.invert();
    var result = callback.get();
    cmp = cmp.invert();
    return result;
  }

  /// Compare two terms with the given {@param type} (if not null)
  ///
  /// @return [ThreeState#YES] if they are 'the same' under {@param type}, [ThreeState#NO] if they are NOT 'the same',
  /// [ThreeState#UNSURE] if not sure, this is typically caused by a failed meta solve.
  public @NotNull ThreeState compare(@Closed @NotNull Term preLhs, @Closed @NotNull Term preRhs, @Closed @Nullable Term type) {
    if (preLhs == preRhs || preLhs instanceof ErrorTerm || preRhs instanceof ErrorTerm) return ThreeState.YES;
    if (checkApproxResult(type, compareApprox(preLhs, preRhs)) == ThreeState.YES) return ThreeState.YES;
    failure = null;

    var lhs = whnf(preLhs);
    var rhs = whnf(preRhs);
    if (!(lhs == preLhs && rhs == preRhs) &&
      checkApproxResult(type, compareApprox(lhs, rhs)) == ThreeState.YES) return ThreeState.YES;

    if (rhs instanceof MetaCall rMeta) {
      // In case we're comparing two metas with one IsType and the other has OfType,
      // prefer solving the IsType one as the OfType one.
      if (lhs instanceof MetaCall lMeta && lMeta.ref().req() == MetaVar.Misc.IsType)
        return solveMeta(lMeta, rMeta, type) != null;
      return swapped(() -> solveMeta(rMeta, lhs, type)) != null;
    }
    // ^ Beware of the order!!
    if (lhs instanceof MetaCall lMeta) {
      return solveMeta(lMeta, rhs, type) != null;
    }

    if (rhs instanceof MemberCall && !(lhs instanceof MemberCall)) {
      return swapped(() -> doCompare(rhs, lhs, type));
    }

    return doCompare(lhs, rhs, type);
  }

  /**
   * Do compare {@param lhs} and {@param rhs} against type {@param type} (if not null),
   * with assumption on a good {@param lhs}, see below.
   *
   * @param lhs is {@link MemberCall} if rhs is not;
   *            if there is a {@link MetaCall} then it must be lhs.
   *            Reason: we case on lhs.
   */
  private @NotNull ThreeState doCompare(Term lhs, Term rhs, @Nullable Term type) {
    var result = type == null
      ? compareUntyped(lhs, rhs).downgrade()
      : doCompareTyped(lhs, rhs, type);

    if (result == ThreeState.NO) fail(lhs, rhs);
    return result;
  }

  /// @param approxResult must with a proof if YES
  private @NotNull ThreeState checkApproxResult(@Nullable Term type, @NotNull RelDec<Term> approxResult) {
    var state = approxResult.downgrade();
    if (state == ThreeState.YES) {
      if (type != null) {
        if (!(approxResult.isYes())) return Panic.unreachable();
        return compare(approxResult.get(), type, null);
      }

      return ThreeState.YES;
    } else return state;
  }

  /// Compare whnf {@param lhs} and whnf {@param rhs} with {@param type} information
  ///
  /// @param type the type in whnf.
  /// @return whether they are 'the same' and their types are {@param type}
  private @NotNull ThreeState doCompareTyped(@Closed @NotNull Term lhs, @Closed @NotNull Term rhs, @Closed @NotNull Term type) {
    return switch (whnf(type)) {
      case LamTerm _, ConCallLike _, TupTerm _ -> Panic.unreachable();
      case ErrorTerm _ -> ThreeState.YES;
      case ClassCall classCall -> {
        if (classCall.args().size() == classCall.ref().members().size()) yield ThreeState.YES;
        // TODO: skip comparing fields that already have impl specified in the type
        // FIXME: not a good idea to use view
        yield classCall.ref().members().view().map(member -> {
          // loop invariant: first [i] members are the "same". ([i] is the loop counter, count from 0)
          // Note that member can only refer to first [i] members, so it is safe that we supply [lhs] or [rhs]
          var ty = member.signature().inst(ImmutableSeq.of(lhs));
          var lproj = MemberCall.make(classCall, lhs, member, 0, ImmutableSeq.empty());
          var rproj = MemberCall.make(classCall, rhs, member, 0, ImmutableSeq.empty());
          return compare(lproj, rproj, ty.makePi(ImmutableSeq.empty()));
        }).fold(ThreeState.YES, ThreeState::min);
      }
      case EqTerm eq -> switch (new Pair<>(lhs, rhs)) {
        case Pair(LamTerm(var lbody), LamTerm(var rbody)) -> {
          try (var scope = subscope(DimTyTerm.INSTANCE)) {
            var var = scope.var();
            yield compare(
              lbody.apply(var),
              rbody.apply(var),
              eq.appA(new FreeTerm(var))
            );
          }
        }
        case Pair(LamTerm lambda, _) -> compareLambda(lambda, rhs, eq);
        case Pair(_, LamTerm rambda) -> compareLambda(rambda, lhs, eq);
        default -> compare(lhs, rhs, null);
      };
      case @Closed DepTypeTerm pi when pi.kind() == DTKind.Pi -> switch (new Pair<>(lhs, rhs)) {
        case Pair(LamTerm(@Closed var lbody), LamTerm(@Closed var rbody)) -> {
          try (var scope = subscope(pi.param())) {
            var var = scope.var();
            yield compare(
              lbody.apply(var),
              rbody.apply(var),
              pi.body().apply(var)
            );
          }
        }
        case Pair(LamTerm lambda, _) -> compareLambda(lambda, rhs, pi);
        case Pair(_, LamTerm rambda) -> compareLambda(rambda, lhs, pi);
        default -> compare(lhs, rhs, null);
      };
      // Sigma types
      case DepTypeTerm(_, @Closed var lTy, @Closed var rTy) -> {
        @Closed var lProj = ProjTerm.fst(lhs);
        @Closed var rProj = ProjTerm.fst(rhs);
        yield compare(lProj, rProj, lTy).lub(() ->
          compare(ProjTerm.snd(lhs), ProjTerm.snd(rhs), rTy.apply(lProj)));
      }
      case PartialTerm(var element1) -> {
        if (!(rhs instanceof PartialTerm(var element2)) || !(type instanceof PartialTyTerm(var r, var s, var A)))
          yield ThreeState.NO;
        yield withConnection(whnf(r), whnf(s), () -> doCompareTyped(element1, element2, A));
      }
      default -> compareUntyped(lhs, rhs).downgrade();
    };
  }

  /// Compare head-normalized {@param preLhs} and whnfed {@param preRhs} without type information.
  ///
  /// @return the head-normalized type of {@param preLhs} and {@param preRhs} if they are _the same_, null otherwise.
  private RelDec.@Closed @NotNull Strict<Term> compareUntyped(@Closed @NotNull Term preLhs, @Closed @NotNull Term preRhs) {
    {
      @Closed var result = compareApprox(preLhs, preRhs);
      if (result.isYes()) return RelDec.of(whnf(result.get()));
      failure = null;
    }

    var lhs = whnf(preLhs);
    var rhs = whnf(preRhs);
    if (!(lhs == preLhs && rhs == preRhs)) {
      @Closed var result = compareCalls(lhs, rhs);
      if (result.isYes()) return RelDec.of(whnf(result.get()));
    }

    @Closed RelDec.Strict<Term> result;
    if (rhs instanceof MetaCall || rhs instanceof MetaLitTerm || rhs instanceof MemberCall) {
      result = swapped(() -> doCompareUntyped(rhs, lhs));
    } else {
      result = doCompareUntyped(lhs, rhs);
    }

    if (result.isYes()) return RelDec.of(whnf(result.get()));
    // TODO: also fail on unsure?
    fail(lhs, rhs);
    return result;
  }

  private @Closed @NotNull RelDec.Strict<Term> doCompareUntyped(@Closed @NotNull Term lhs, @Closed @NotNull Term rhs) {
    if (lhs instanceof @Closed Formation form)
      return doCompareType(form, rhs).toRelDec(() -> {
        // It's going to be used in the synthesizer, so we freeze it first
        return new Synthesizer(this).synthDontNormalize(freezeHoles(form));
      });

    return switch (lhs) {
      case AppTerm(var f, var a) -> {
        if (!(rhs instanceof AppTerm(var g, var b))) yield RelDec.no();
        var fTy = compareUntyped(f, g);
        // TODO: not sure
        if (!fTy.isYes()) yield RelDec.no();
        if (!(fTy.get() instanceof DepTypeTerm(var kk, var param, var body) && kk == DTKind.Pi)) yield RelDec.no();
        yield compare(a, b, param).toRelDec(() ->
          body.apply(a));
      }
      case PAppTerm(var f, var a, _, _) -> {
        if (!(rhs instanceof PAppTerm(var g, var b, _, _))) yield RelDec.no();
        var fTy = compareUntyped(f, g);
        // TODO: not sure
        if (!fTy.isYes()) yield RelDec.no();
        if (!(fTy.get() instanceof EqTerm eq)) yield RelDec.no();
        yield compare(a, b, DimTyTerm.INSTANCE).toRelDec(() ->
          eq.appA(a));
      }
      case CoeTerm coe -> {
        if (!(rhs instanceof CoeTerm(var rType, var rR, var rS))) yield RelDec.no();

        // TODO: not sure
        var result = compare(coe.r(), rR, DimTyTerm.INSTANCE);
        if (result == ThreeState.NO) yield RelDec.no();
        result = compare(coe.s(), rS, DimTyTerm.INSTANCE);
        if (result != ThreeState.NO) yield RelDec.no();

        try (var scope = subscope(DimTyTerm.INSTANCE)) {
          var var = scope.var();
          var tyResult = compare(coe.type().apply(var), rType.apply(var), null);
          yield tyResult.toRelDec(coe.family());
        }
      }
      case ProjTerm(var lof, var lfst) -> {
        // Since {lhs} and {rhs} are whnf, at this point, {lof} is unable to evaluate.
        // Thus the only thing we can do is check whether {lof} and {rhs.of(}} (if rhs is ProjTerm) are 'the same'.
        if (!(rhs instanceof ProjTerm(var rof, var rfst))) yield RelDec.no();
        var result = compareUntyped(lof, rof);
        // TODO: not sure
        if (!result.isYes()) yield result;
        if (!(result.get() instanceof DepTypeTerm(var k, var lhsT, var rhsTClos) && k == DTKind.Sigma))
          yield RelDec.no();
        if (lfst != rfst) yield RelDec.no();
        if (lfst) yield RelDec.of(lhsT);
        yield RelDec.of(rhsTClos.apply(new ProjTerm(lof, true)));
      }
      case FreeTerm(var lvar) -> rhs instanceof FreeTerm(var rvar) && lvar == rvar
        ? RelDec.of(localCtx().get(lvar))
        : RelDec.no();
      case DimTerm l -> rhs instanceof DimTerm r && l == r ? RelDec.of(l) : RelDec.no();
      case MetaCall mCall -> solveMeta(mCall, rhs, null);
      // By typing invariant, they should have the same type, so no need to check for repr equality.
      case IntegerTerm(var lepr, _, _, var ty) -> rhs instanceof IntegerTerm rInt && lepr == rInt.repr()
        ? RelDec.of(ty)
        : RelDec.no();
      case ListTerm list -> switch (rhs) {
        case ListTerm rist -> {
          var lRepr = list.repr();
          var rRepr = list.repr();

          if (!lRepr.sizeEquals(rRepr)) yield RelDec.no();

          var result = compareMany(lRepr, rRepr, null).downgrade();
          yield result.toRelDec(list.type());
        }
        case ConCall rCon -> compareUntyped(list.constructorForm(), rCon);
        default -> RelDec.no();
      };
      // fallback case
      case ConCallLike lCon -> switch (rhs) {
        case ListTerm rList -> compareUntyped(lhs, rList.constructorForm());
        case ConCallLike rCon -> compareCallApprox(lCon, rCon);
        default -> RelDec.no();
      };
      case MetaLitTerm mlt -> switch (rhs) {
        case IntegerTerm mrt -> compareMetaLitWithLit(mlt, mrt.repr(), mrt.type());
        case ListTerm mrt -> compareMetaLitWithLit(mlt, mrt.repr(), mrt.type());
        case ConCall _ -> throw new UnsupportedOperationException("TODO (I have no time to implement this)");
        case MetaLitTerm mrt -> compareMetaLitWithLit(mlt, mrt.repr(), mrt.type());
        default -> RelDec.no();
      };
      case MemberCall memberCall -> {
        // it is impossible that memberCall.of() is a cast term, since it is whnfed.
        assert !(memberCall.of() instanceof ClassCastTerm);
        if (rhs instanceof MemberCall memberCarr) {
          assert !(memberCarr.of() instanceof ClassCastTerm);
          yield compareUntyped(memberCall.of(), memberCarr.of());
        } else {
          yield RelDec.no();
        }
      }
      // We already compare arguments in compareApprox, if we arrive here,
      // it means their arguments don't match (even the refs match),
      // so we are unable to do more if we can't normalize them.
      case FnCall _, RuleReducer _, PrimCall _ -> RelDec.no();

      default -> throw noRules(lhs);
    };
  }

  private RelDec.@Closed @NotNull Strict<Term> compareMetaLitWithLit(@NotNull MetaLitTerm lhs, Object repr, @NotNull Term rhsType) {
    if (!Objects.equals(lhs.repr(), repr)) return RelDec.no();
    return compare(lhs.type(), rhsType, null).toRelDec(lhs.type());
  }

  /** Compare {@param lambda} and {@param rhs} with {@param type} */
  private @NotNull ThreeState compareLambda(@Closed @NotNull LamTerm lambda, @Closed @NotNull Term rhs, @Closed @NotNull DepTypeTerm type) {
    try (var scope = subscope(type.param())) {
      var var = scope.var();
      @Closed var lhsBody = lambda.body().apply(var);
      @Closed var rhsBody = AppTerm.make(rhs, new FreeTerm(var));
      return compare(lhsBody, rhsBody, type.body().apply(var));
    }
  }

  private @NotNull ThreeState compareLambda(@Closed @NotNull LamTerm lambda, @Closed @NotNull Term rhs, @Closed @NotNull EqTerm type) {
    try (var scope = subscope(DimTyTerm.INSTANCE)) {
      var var = scope.var();
      @Closed var lhsBody = lambda.body().apply(var);
      @Closed var free = new FreeTerm(var);
      @Closed var rhsBody = AppTerm.make(rhs, free);
      return compare(lhsBody, rhsBody, type.appA(free));
    }
  }

  /// @return `Proof(null)` if `types` == null
  private RelDec.@Closed @NotNull Strict<@Nullable Term> compareMany(
    @NotNull ImmutableSeq<@Closed Term> list,
    @NotNull ImmutableSeq<@Closed Term> rist,
    @Closed @Nullable AbstractTele types
  ) {
    assert list.sizeEquals(rist);
    assert types == null || rist.sizeEquals(types.telescopeSize());
    var argsCum = new Term[list.size()];

    var ret = ThreeState.YES;

    for (var i = 0; i < argsCum.length; ++i) {
      @Closed var l = list.get(i);
      @Closed var r = rist.get(i);
      @Closed @Nullable var ty = types == null ? null : types.telescope(i, argsCum);
      ret = ret.lub(compare(l, r, ty));
      // TODO: not sure if we should continue when UNSURE
      if (ret == ThreeState.NO) return RelDec.no();
      argsCum[i] = l;
    }

    return ret.toRelDec(() -> types == null ? null : types.result(argsCum));
  }

  /**
   * Compare {@param lTy} and {@param rTy}
   *
   * @param continuation invoked with {@code ? : lTy} in {@link Contextful#localCtx()} if {@param lTy} is the 'same' as {@param rTy}
   */
  private <R> R compareTypeWith(
    @Closed @NotNull Term lTy,
    @Closed @NotNull Term rTy,
    @NotNull Supplier<R> onFailed,
    @NotNull Function<LocalVar, R> continuation
  ) {
    if (compare(lTy, rTy, null) == ThreeState.NO) return onFailed.get();
    try (var scope = subscope(lTy)) {
      var var = scope.var();
      return continuation.apply(var);
    }
  }

  private boolean sortLt(@NotNull SortTerm l, @NotNull SortTerm r) {
    var lift = l.lift();
    var rift = r.lift();
    // ISet <= Set0
    // Set i <= Set j if i <= j
    // Type i <= Type j if i <= j
    return switch (l.kind()) {
      case Type -> r.kind() == SortKind.Type && lift <= rift;
      case ISet -> r.kind() == SortKind.Set || r.kind() == SortKind.ISet;
      case Set -> r.kind() == SortKind.Set && lift <= rift;
    };
  }

  private @NotNull ThreeState compareSort(@Closed @NotNull SortTerm l, @Closed @NotNull SortTerm r) {
    return switch (cmp) {
      case Gt -> {
        if (!sortLt(r, l)) {
          fail(new LevelError(pos, l, r, false));
          yield ThreeState.NO;
        } else yield ThreeState.YES;
      }
      case Eq -> {
        if (!(l.kind() == r.kind() && l.lift() == r.lift())) {
          fail(new LevelError(pos, l, r, true));
          yield ThreeState.NO;
        } else yield ThreeState.YES;
      }
      case Lt -> {
        if (!sortLt(l, r)) {
          fail(new LevelError(pos, r, l, false));
          yield ThreeState.NO;
        } else yield ThreeState.YES;
      }
    };
  }

  /**
   * Compare two type formation
   * Note: don't confuse with {@link TermComparator#doCompareTyped(Term, Term, Term)}
   */
  private @NotNull ThreeState doCompareType(@Closed @NotNull Formation preLhs, @Closed @NotNull Term preRhs) {
    if (preLhs.getClass() != preRhs.getClass()) return ThreeState.NO;
    return switch (new Pair<>(preLhs, (Formation) preRhs)) {
      case Pair(DataCall lhs, DataCall rhs) -> compareCallApprox(lhs, rhs).downgrade();
      case Pair(DimTyTerm _, DimTyTerm _) -> ThreeState.YES;
      case Pair(
        DepTypeTerm(var lK, @Closed var lParam, @Closed var lBody),
        DepTypeTerm(var rK, @Closed var rParam, @Closed var rBody)
      ) -> lK == rK
        ? compareTypeWith(lParam, rParam, () -> ThreeState.NO, var ->
        compare(lBody.apply(var), rBody.apply(var), null))
        : ThreeState.NO;
      case Pair(SortTerm lhs, SortTerm rhs) -> compareSort(lhs, rhs);
      case Pair(
        EqTerm(@Closed var A, @Closed var a0, @Closed var a1), EqTerm(@Closed var B, @Closed var b0, @Closed var b1)
      ) -> {
        var tyResult = ThreeState.YES;
        try (var scope = subscope(DimTyTerm.INSTANCE)) {
          var var = scope.var();
          tyResult = ThreeState.min(tyResult, compare(A.apply(var), B.apply(var), null));
        }

        if (tyResult == ThreeState.NO) yield ThreeState.NO;
        // the behavior is not exact the same as before, `&&` is shortcut but `min` isn't
        yield ThreeState.min(compare(a0, b0, A.apply(DimTerm.I0)), compare(a1, b1, A.apply(DimTerm.I1)));
      }
      case Pair(
        PartialTyTerm(@Closed var lhs1, @Closed var rhs1, @Closed var A1),
        PartialTyTerm(@Closed var lhs2, @Closed var rhs2, @Closed var A2)
      ) -> {
        @Closed var wl2 = whnf(lhs2);
        @Closed var wr2 = whnf(rhs2);
        if (logicallyInequivalent(whnf(lhs1), whnf(rhs1), wl2, wr2)) yield ThreeState.NO;
        yield withConnection(wl2, wr2, () -> compare(A1, A2, null));
      }
      default -> throw noRules(preLhs);
    };
  }

  /// Params are assumed to be in whnf
  private boolean logicallyInequivalent(@Closed Term wl1, @Closed Term wr1, @Closed Term wl2, @Closed Term wr2) {
    // lhs1 = rhs1 ==> lhs2 = rhs2
    var to = withConnection(wl1, wr1, () -> state.isConnected(wl2, wr2));
    if (!to) return true;
    // lhs1 = rhs1 <== lhs2 = rhs2
    var from = withConnection(wl2, wr2, () -> state.isConnected(wl1, wr1));
    return !from;
  }

  public @NotNull SubscopedVar subscope(@NotNull Term type) {
    return super.subscope(type, nameGen);
  }

  public @NotNull FailureData getFailure() {
    var failure = this.failure;
    assert failure != null;
    return failure.map(this::freezeHoles);
  }

  public record FailureData(@NotNull Term lhs, @NotNull Term rhs) {
    public @NotNull FailureData map(@NotNull UnaryOperator<Term> f) {
      return new FailureData(f.apply(lhs), f.apply(rhs));
    }
  }

  /** Maybe you're looking for {@link #compare} or {@link TyckState#solveEqn} instead. */
  @ApiStatus.Internal public @NotNull ThreeState checkEqn(@NotNull TyckState.Eqn eqn) {
    if (state.solutions.containsKey(eqn.lhs().ref()))
      return compare(eqn.lhs(), eqn.rhs(), eqn.type());
    else return solveMeta(eqn.lhs(), eqn.rhs(), eqn.type()) != null;
  }
}
