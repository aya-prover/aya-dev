// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.unify;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.generic.Renamer;
import org.aya.generic.term.DTKind;
import org.aya.generic.term.SortKind;
import org.aya.prettier.AyaPrettierOptions;
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
import org.aya.tyck.TyckState;
import org.aya.tyck.error.LevelError;
import org.aya.tyck.tycker.AbstractTycker;
import org.aya.tyck.tycker.Contextful;
import org.aya.util.Ordering;
import org.aya.util.Pair;
import org.aya.util.error.Panic;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public abstract sealed class TermComparator extends AbstractTycker permits Unifier {
  protected final @NotNull SourcePos pos;
  protected @NotNull Ordering cmp;
  private @Nullable FailureData failure = null;
  final @NotNull Renamer nameGen = new Renamer();

  private enum MetaStrategy {
    Default,
    Trying,
    NonInjective
  }
  private @NotNull TermComparator.MetaStrategy strategy = MetaStrategy.Default;
  private final MutableList<TyckState.Eqn> weWillSee = MutableList.create();
  private boolean stuckOnMeta = false;

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
   * @param rhs in whnf, must not be the same meta as {@param meta}
   */
  protected abstract @Nullable Term doSolveMeta(@NotNull MetaCall meta, @NotNull Term rhs, @Nullable Term type);

  /** The "flex-flex" case with identical meta ref */
  private @Nullable Term sameMeta(@NotNull MetaCall meta, @Nullable Term type, MetaCall rMeta) {
    if (meta.args().size() != rMeta.args().size()) return null;
    for (var i = 0; i < meta.args().size(); i++) {
      if (!compare(meta.args().get(i), rMeta.args().get(i), null)) {
        return null;
      }
    }
    if (type != null) return type;
    if (meta.ref().req() instanceof MetaVar.OfType(var ty)) return ty;
    return ErrorTerm.typeOf(meta);
  }

  public @NotNull TyckState.Eqn createEqn(@NotNull MetaCall lhs, @NotNull Term rhs, @Nullable Term type) {
    return new TyckState.Eqn(lhs, rhs, type, cmp, pos, localCtx().clone());
  }

  protected @Nullable Term solveMeta(@NotNull MetaCall meta, @NotNull Term rhs, @Nullable Term type) {
    if (rhs instanceof MetaCall rMeta && rMeta.ref() == meta.ref())
      return sameMeta(meta, type, rMeta);

    return switch (strategy) {
      case Default -> {
        var result = doSolveMeta(meta, whnf(rhs), type);
        if (result == null) fail(meta, rhs);
        yield result;
      }
      case Trying -> {
        weWillSee.append(createEqn(meta, rhs, type));
        // This is a bit sus
        yield type != null ? type : ErrorTerm.typeOf(meta);
      }
      case NonInjective -> {
        stuckOnMeta = true;
        yield null;
      }
    };
  }

  /// region Utilities
  private void fail(@NotNull Term lhs, @NotNull Term rhs) {
    if (failure == null) {
      failure = new FailureData(lhs, rhs);
    }
  }

  private @NotNull Panic noRules(@NotNull Term term) {
    return new Panic(term.getClass() + ": " + term.toDoc(AyaPrettierOptions.debug()).debugRender());
  }
  /// endregion Utilities

  /**
   * Compare arguments ONLY.
   * For lossy comparisons, when we fail, we will need to compare them again later,
   * so don't forget to reset the {@link #failure} after first failure.
   */
  private @Nullable Term compareApprox(@NotNull Term lhs, @NotNull Term rhs) {
    var wantFinalize = false;
    switch (strategy) {
      case Default -> {
        wantFinalize = true;
        strategy = MetaStrategy.Trying;
        assert weWillSee.isEmpty();
      }
      case Trying -> { }
      case NonInjective -> { }
    }
    var result = switch (new Pair<>(lhs, rhs)) {
      case Pair(FnCall lFn, FnCall rFn) -> compareCallApprox(lFn, rFn);
      case Pair(DataCall lFn, DataCall rFn) -> compareCallApprox(lFn, rFn);
      case Pair(PrimCall lFn, PrimCall rFn) -> compareCallApprox(lFn, rFn);
      case Pair(IntegerTerm lInt, IntegerTerm rInt) -> lInt.repr() == rInt.repr() ? lInt.type() : null;
      case Pair(ConCallLike lCon, ConCallLike rCon) -> compareCallApprox(lCon, rCon);
      case Pair(MemberCall lMem, MemberCall rMem) -> {
        if (!lMem.ref().equals(rMem.ref())) yield null;
        // TODO: type info?
        if (!compare(lMem.of(), rMem.of(), null)) yield null;
        yield compareMany(lMem.args(), rMem.args(),
          lMem.ref().signature().inst(ImmutableSeq.of(lMem.of())).lift(Math.min(lMem.ulift(), rMem.ulift())));
      }
      default -> null;
    };
    if (wantFinalize) {
      strategy = MetaStrategy.Default;
      if (stuckOnMeta) {
        for (var eqn : weWillSee) state.addEqn(eqn);
      } else {
        for (var eqn : weWillSee) checkEqn(eqn);
      }
      weWillSee.clear();
    }
    return result;
  }

  /**
   * Compare the arguments of two callable ONLY, this method will NOT try to normalize and then compare (while the old project does).
   */
  private @Nullable Term compareCallApprox(@NotNull Callable.Tele lhs, @NotNull Callable.Tele rhs) {
    if (!lhs.ref().equals(rhs.ref())) return null;
    return compareMany(lhs.args(), rhs.args(),
      lhs.ref().signature().lift(Math.min(lhs.ulift(), rhs.ulift())));
  }

  private <R> R swapped(@NotNull Supplier<R> callback) {
    cmp = cmp.invert();
    var result = callback.get();
    cmp = cmp.invert();
    return result;
  }

  /**
   * Compare two terms with the given {@param type} (if not null)
   *
   * @return true if they are 'the same' under {@param type}, false otherwise.
   */
  public boolean compare(@NotNull Term preLhs, @NotNull Term preRhs, @Nullable Term type) {
    if (preLhs == preRhs || preLhs instanceof ErrorTerm || preRhs instanceof ErrorTerm) return true;
    if (checkApproxResult(type, compareApprox(preLhs, preRhs))) return true;
    failure = null;

    var lhs = whnf(preLhs);
    var rhs = whnf(preRhs);
    if (!(lhs == preLhs && rhs == preRhs) &&
      checkApproxResult(type, compareApprox(lhs, rhs))) return true;

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
  private boolean doCompare(Term lhs, Term rhs, @Nullable Term type) {
    var result = type == null
      ? compareUntyped(lhs, rhs) != null
      : doCompareTyped(lhs, rhs, type);

    if (!result) fail(lhs, rhs);
    return result;
  }

  private boolean checkApproxResult(@Nullable Term type, Term approxResult) {
    if (approxResult != null) {
      if (type != null) compare(approxResult, type, null);
      return true;
    } else return false;
  }

  /**
   * Compare whnf {@param lhs} and whnf {@param rhs} with {@param type} information
   *
   * @param type the whnf type.
   * @return whether they are 'the same' and their types are {@param type}
   */
  private boolean doCompareTyped(@NotNull Term lhs, @NotNull Term rhs, @NotNull Term type) {
    return switch (whnf(type)) {
      case LamTerm _, ConCallLike _, TupTerm _ -> Panic.unreachable();
      case ErrorTerm _ -> true;
      case ClassCall classCall -> {
        if (classCall.args().size() == classCall.ref().members().size()) yield true;
        // TODO: skip comparing fields that already have impl specified in the type
        yield classCall.ref().members().allMatch(member -> {
          // loop invariant: first [i] members are the "same". ([i] is the loop counter, count from 0)
          // Note that member can only refer to first [i] members, so it is safe that we supply [lhs] or [rhs]
          var ty = member.signature().inst(ImmutableSeq.of(lhs));
          var lproj = MemberCall.make(classCall, lhs, member, 0, ImmutableSeq.empty());
          var rproj = MemberCall.make(classCall, rhs, member, 0, ImmutableSeq.empty());
          return compare(lproj, rproj, ty.makePi(ImmutableSeq.empty()));
        });
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
      case DepTypeTerm pi when pi.kind() == DTKind.Pi -> switch (new Pair<>(lhs, rhs)) {
        case Pair(LamTerm(var lbody), LamTerm(var rbody)) -> {
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
      case DepTypeTerm(_, var lTy, var rTy) -> {
        var lProj = ProjTerm.fst(lhs);
        var rProj = ProjTerm.fst(rhs);
        if (!compare(lProj, rProj, lTy)) yield false;
        yield compare(ProjTerm.snd(lhs), ProjTerm.snd(rhs), rTy.apply(lProj));
      }
      default -> compareUntyped(lhs, rhs) != null;
    };
  }

  /**
   * Compare head-normalized {@param preLhs} and whnfed {@param preRhs} without type information.
   *
   * @return the head-normalized type of {@param preLhs} and {@param preRhs} if they are 'the same', null otherwise.
   */
  private @Nullable Term compareUntyped(@NotNull Term preLhs, @NotNull Term preRhs) {
    {
      var result = compareApprox(preLhs, preRhs);
      if (result != null) return whnf(result);
      failure = null;
    }

    var lhs = whnf(preLhs);
    var rhs = whnf(preRhs);
    if (!(lhs == preLhs && rhs == preRhs)) {
      var result = compareApprox(lhs, rhs);
      if (result != null) return whnf(result);
    }

    Term result;
    if (rhs instanceof MetaCall || rhs instanceof MetaLitTerm || rhs instanceof MemberCall) {
      result = swapped(() -> doCompareUntyped(rhs, lhs));
    } else {
      result = doCompareUntyped(lhs, rhs);
    }
    if (result != null) return whnf(result);
    fail(lhs, rhs);
    return null;
  }

  private @Nullable Term doCompareUntyped(@NotNull Term lhs, @NotNull Term rhs) {
    if (lhs instanceof Formation form)
      return doCompareType(form, rhs) ?
        // It's going to be used in the synthesizer, so we freeze it first
        new Synthesizer(this).synthDontNormalize(freezeHoles(form)) : null;
    return switch (lhs) {
      case AppTerm(var f, var a) -> {
        var prev = strategy;
        switch (strategy) {
          case Default, Trying -> strategy = MetaStrategy.NonInjective;
          case NonInjective -> { }
        }
        try {
          if (!(rhs instanceof AppTerm(var g, var b))) yield null;
          var fTy = compareUntyped(f, g);
          if (!(fTy instanceof DepTypeTerm(var kk, var param, var body) && kk == DTKind.Pi)) yield null;
          if (!compare(a, b, param)) yield null;
          yield body.apply(a);
        } finally {
          strategy = prev;
          // TODO: this is wrong, needs to be rewritten using exceptions
          if (stuckOnMeta) {
            // state.addEqn(createEqn(lhs, rhs, null));
          }
        }
      }
      case PAppTerm(var f, var a, _, _) -> {
        if (!(rhs instanceof PAppTerm(var g, var b, _, _))) yield null;
        var fTy = compareUntyped(f, g);
        if (!(fTy instanceof EqTerm eq)) yield null;
        if (!compare(a, b, DimTyTerm.INSTANCE)) yield null;
        yield eq.appA(a);
      }
      case CoeTerm coe -> {
        if (!(rhs instanceof CoeTerm(var rType, var rR, var rS))) yield null;
        if (!compare(coe.r(), rR, DimTyTerm.INSTANCE)) yield null;
        if (!compare(coe.s(), rS, DimTyTerm.INSTANCE)) yield null;
        try (var scope = subscope(DimTyTerm.INSTANCE)) {
          var var = scope.var();
          var tyResult = compare(coe.type().apply(var), rType.apply(var), null);
          yield tyResult ? coe.family() : null;
        }
      }
      case ProjTerm(var lof, var ldx) -> {
        // Since {lhs} and {rhs} are whnf, at this point, {lof} is unable to evaluate.
        // Thus the only thing we can do is check whether {lof} and {rhs.of(}} (if rhs is ProjTerm) are 'the same'.
        if (!(rhs instanceof ProjTerm(var rof, var rdx))) yield null;
        if (!(compareUntyped(lof, rof) instanceof DepTypeTerm(var k, var lhsT, var rhsTClos) && k == DTKind.Sigma))
          yield null;
        if (ldx != rdx) yield null;
        if (ldx) yield lhsT;
        yield rhsTClos.apply(new ProjTerm(lof, true));
      }
      case FreeTerm(var lvar) -> rhs instanceof FreeTerm(var rvar) && lvar == rvar ? localCtx().get(lvar) : null;
      case DimTerm l -> rhs instanceof DimTerm r && l == r ? l : null;
      case MetaCall mCall -> solveMeta(mCall, rhs, null);
      // By typing invariant, they should have the same type, so no need to check for repr equality.
      case IntegerTerm(var lepr, _, _, var ty) -> rhs instanceof IntegerTerm rInt && lepr == rInt.repr() ? ty : null;
      case ListTerm list -> switch (rhs) {
        case ListTerm rist -> {
          if (!list.compareUntyped(rist, (l, r) ->
            compare(l, r, null))) yield null;
          yield list.type();
        }
        case ConCall rCon -> compareUntyped(list.constructorForm(), rCon);
        default -> null;
      };
      // fallback case
      case ConCallLike lCon -> switch (rhs) {
        case ListTerm rList -> compareUntyped(lhs, rList.constructorForm());
        case ConCallLike rCon -> compareCallApprox(lCon, rCon);
        default -> null;
      };
      case MetaLitTerm mlt -> switch (rhs) {
        case IntegerTerm mrt -> compareMetaLitWithLit(mlt, mrt.repr(), mrt.type());
        case ListTerm mrt -> compareMetaLitWithLit(mlt, mrt.repr(), mrt.type());
        case ConCall _ -> throw new UnsupportedOperationException("TODO (I have no time to implement this)");
        case MetaLitTerm mrt -> compareMetaLitWithLit(mlt, mrt.repr(), mrt.type());
        default -> null;
      };
      case MemberCall memberCall -> {
        // it is impossible that memberCall.of() is a cast term, since it is whnfed.
        assert !(memberCall.of() instanceof ClassCastTerm);
        if (rhs instanceof MemberCall memberCarr) {
          assert !(memberCarr.of() instanceof ClassCastTerm);
          yield compareUntyped(memberCall.of(), memberCarr.of());
        } else {
          yield null;
        }
      }
      // We already compare arguments in compareApprox, if we arrive here,
      // it means their arguments don't match (even the refs match),
      // so we are unable to do more if we can't normalize them.
      case FnCall _, PrimCall _ -> null;

      default -> throw noRules(lhs);
    };
  }

  private @Nullable Term compareMetaLitWithLit(@NotNull MetaLitTerm lhs, Object repr, @NotNull Term rhsType) {
    if (!Objects.equals(lhs.repr(), repr)) return null;
    if (compare(lhs.type(), rhsType, null)) return lhs.type();
    return null;
  }

  /** Compare {@param lambda} and {@param rhs} with {@param type} */
  private boolean compareLambda(@NotNull LamTerm lambda, @NotNull Term rhs, @NotNull DepTypeTerm type) {
    try (var scope = subscope(type.param())) {
      var var = scope.var();
      var lhsBody = lambda.body().apply(var);
      var rhsBody = AppTerm.make(rhs, new FreeTerm(var));
      return compare(lhsBody, rhsBody, type.body().apply(var));
    }
  }

  private boolean compareLambda(@NotNull LamTerm lambda, @NotNull Term rhs, @NotNull EqTerm type) {
    try (var scope = subscope(DimTyTerm.INSTANCE)) {
      var var = scope.var();
      var lhsBody = lambda.body().apply(var);
      var free = new FreeTerm(var);
      var rhsBody = AppTerm.make(rhs, free);
      return compare(lhsBody, rhsBody, type.appA(free));
    }
  }

  private @Nullable Term compareMany(
    @NotNull ImmutableSeq<Term> list,
    @NotNull ImmutableSeq<Term> rist,
    @NotNull AbstractTele types
  ) {
    assert list.sizeEquals(rist);
    assert rist.sizeEquals(types.telescopeSize());
    var argsCum = new Term[types.telescopeSize()];

    for (var i = 0; i < types.telescopeSize(); ++i) {
      var l = list.get(i);
      var r = rist.get(i);
      var ty = types.telescope(i, argsCum);
      if (!compare(l, r, ty)) return null;
      argsCum[i] = l;
    }

    return types.result(argsCum);
  }

  /**
   * Compare {@param lTy} and {@param rTy}
   *
   * @param continuation invoked with {@code ? : lTy} in {@link Contextful#localCtx()} if {@param lTy} is the 'same' as {@param rTy}
   */
  private <R> R compareTypeWith(
    @NotNull Term lTy,
    @NotNull Term rTy,
    @NotNull Supplier<R> onFailed,
    @NotNull Function<LocalVar, R> continuation
  ) {
    if (!compare(lTy, rTy, null)) return onFailed.get();
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

  private boolean compareSort(@NotNull SortTerm l, @NotNull SortTerm r) {
    return switch (cmp) {
      case Gt -> {
        if (!sortLt(r, l)) {
          fail(new LevelError(pos, l, r, false));
          yield false;
        } else yield true;
      }
      case Eq -> {
        if (!(l.kind() == r.kind() && l.lift() == r.lift())) {
          fail(new LevelError(pos, l, r, true));
          yield false;
        } else yield true;
      }
      case Lt -> {
        if (!sortLt(l, r)) {
          fail(new LevelError(pos, r, l, false));
          yield false;
        } else yield true;
      }
    };
  }

  /**
   * Compare two type formation
   * Note: don't confuse with {@link TermComparator#doCompareTyped(Term, Term, Term)}
   */
  private boolean doCompareType(@NotNull Formation preLhs, @NotNull Term preRhs) {
    if (preLhs.getClass() != preRhs.getClass()) return false;
    return switch (new Pair<>(preLhs, (Formation) preRhs)) {
      case Pair(DataCall lhs, DataCall rhs) -> compareCallApprox(lhs, rhs) != null;
      case Pair(DimTyTerm _, DimTyTerm _) -> true;
      case Pair(DepTypeTerm(var lK, var lParam, var lBody), DepTypeTerm(var rK, var rParam, var rBody)) ->
        lK == rK && compareTypeWith(lParam, rParam, () -> false, var ->
          compare(lBody.apply(var), rBody.apply(var), null));
      case Pair(SortTerm lhs, SortTerm rhs) -> compareSort(lhs, rhs);
      case Pair(EqTerm(var A, var a0, var a1), EqTerm(var B, var b0, var b1)) -> {
        var tyResult = false;
        try (var scope = subscope(DimTyTerm.INSTANCE)) {
          var var = scope.var();
          tyResult = compare(A.apply(var), B.apply(var), null);
        }
        if (!tyResult) yield false;
        yield compare(a0, b0, A.apply(DimTerm.I0)) && compare(a1, b1, A.apply(DimTerm.I1));
      }
      default -> throw noRules(preLhs);
    };
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

  /** Maybe you're looking for {@link #compare} instead. */
  @ApiStatus.Internal public boolean checkEqn(@NotNull TyckState.Eqn eqn) {
    if (state.solutions.containsKey(eqn.lhs().ref()))
      return compare(eqn.lhs(), eqn.rhs(), eqn.type());
    else return solveMeta(eqn.lhs(), eqn.rhs(), eqn.type()) != null;
  }
}
