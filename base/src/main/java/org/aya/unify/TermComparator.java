// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.unify;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.NameGenerator;
import org.aya.generic.stmt.Shaped;
import org.aya.generic.term.SortKind;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.syntax.compile.JitTele;
import org.aya.syntax.core.def.AnyDef;
import org.aya.syntax.core.def.TyckDef;
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
  // If false, we refrain from solving meta, and return false if we encounter a non-identical meta.
  private boolean solveMeta = true;
  private @Nullable FailureData failure = null;
  private final @NotNull NameGenerator nameGen = new NameGenerator();

  public TermComparator(
    @NotNull TyckState state, @NotNull LocalCtx ctx,
    @NotNull Reporter reporter, @NotNull SourcePos pos, @NotNull Ordering cmp
  ) {
    super(state, ctx, reporter);
    this.pos = pos;
    this.cmp = cmp;
  }

  /**
   * Trying to solve {@param meta} with {@param rhs}
   *
   * @param rhs in whnf
   */
  protected abstract @Nullable Term doSolveMeta(@NotNull MetaCall meta, @NotNull Term rhs, @Nullable Term type);

  protected @Nullable Term solveMeta(@NotNull MetaCall meta, @NotNull Term rhs, @Nullable Term type) {
    var result = !solveMeta ? null : doSolveMeta(meta, whnf(rhs), type);
    if (result == null) fail(meta, rhs);
    return result;
  }

  /// region Utilities
  private void fail(@NotNull Term lhs, @NotNull Term rhs) {
    if (failure == null) {
      failure = new FailureData(lhs, rhs);
    }
  }

  private @NotNull Panic noRules(@NotNull Term term) {
    return new Panic(STR."\{term.getClass()}: \{term.toDoc(AyaPrettierOptions.debug()).debugRender()}");
  }
  /// endregion Utilities

  /**
   * Compare arguments ONLY.
   * For lossy comparisons, when we fail, we will need to compare them again later,
   * so don't forget to reset the {@link #failure} after first failure.
   */
  private @Nullable Term compareApprox(@NotNull Term lhs, @NotNull Term rhs) {
    return switch (new Pair<>(lhs, rhs)) {
      case Pair(FnCall lFn, FnCall rFn) -> compareCallApprox(lFn, rFn, lFn.ref());
      case Pair(PrimCall lFn, PrimCall rFn) -> compareCallApprox(lFn, rFn, lFn.ref());
      case Pair(IntegerTerm lInt, IntegerTerm rInt) ->
        lInt.repr() == ((Shaped.@NotNull Nat<Term>) rInt).repr() ? lInt : null;
      case Pair(ConCallLike lCon, ConCallLike rCon) -> compareCallApprox(lCon, rCon, lCon.ref());
      default -> null;
    };
  }

  /**
   * Compare the arguments of two callable ONLY, this method will NOT try to normalize and then compare (while the old project does).
   */
  private @Nullable Term compareCallApprox(
    @NotNull Callable.Tele lhs, @NotNull Callable.Tele rhs, @NotNull AnyDef typeProvider
  ) {
    if (!lhs.ref().equals(rhs.ref())) return null;
    return compareMany(lhs.args(), rhs.args(), lhs.ulift(), TyckDef.defSignature(typeProvider));
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
    if ((!(lhs == preLhs && rhs == preRhs)) &&
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
    } else if (type == null) {
      return compareUntyped(lhs, rhs) != null;
    }

    var result = doCompareTyped(lhs, rhs, type);
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
    return switch (type) {
      // TODO: ClassCall
      case LamTerm _, ConCallLike _, TupTerm _ -> Panic.unreachable();
      case ErrorTerm _ -> true;
      case PiTerm pi -> switch (new Pair<>(lhs, rhs)) {
        case Pair(LamTerm(var lbody), LamTerm(var rbody)) -> subscoped(() -> {
          var var = putIndex(pi.param());
          return compare(
            lbody.apply(var),
            rbody.apply(var),
            pi.body().apply(var)
          );
        });
        case Pair(LamTerm lambda, _) -> compareLambda(lambda, rhs, pi);
        case Pair(_, LamTerm rambda) -> compareLambda(rambda, lhs, pi);
        default -> compare(lhs, rhs, null);
      };
      case EqTerm eq -> switch (new Pair<>(lhs, rhs)) {
        case Pair(LamTerm(var lbody), LamTerm(var rbody)) -> subscoped(() -> {
          var var = putIndex(DimTyTerm.INSTANCE);
          return compare(
            lbody.apply(var),
            rbody.apply(var),
            eq.appA(new FreeTerm(var))
          );
        });
        case Pair(LamTerm lambda, _) -> compareLambda(lambda, rhs, eq);
        case Pair(_, LamTerm rambda) -> compareLambda(rambda, lhs, eq);
        default -> compare(lhs, rhs, null);
      };
      case SigmaTerm(var paramSeq) -> {
        var size = paramSeq.size();
        var list = ImmutableSeq.fill(size, i -> ProjTerm.make(lhs, i));
        var rist = ImmutableSeq.fill(size, i -> ProjTerm.make(rhs, i));

        var telescopic = new JitTele.LocallyNameless(paramSeq.map(p -> new Param("_", p, true)), ErrorTerm.DUMMY);
        yield compareMany(list, rist, 0, telescopic) != null;
      }
      default -> compareUntyped(lhs, rhs) != null;
    };
  }

  /**
   * Compare whnfed {@param preLhs} and whnfed {@param preRhs} without type information.
   *
   * @return the whnfed type of {@param preLhs} and {@param preRhs} if they are 'the same', null otherwise.
   */
  private @Nullable Term compareUntyped(@NotNull Term preLhs, @NotNull Term preRhs) {
    {
      var result = compareApprox(preLhs, preRhs);
      if (result != null) return result;
      failure = null;
    }

    var lhs = whnf(preLhs);
    var rhs = whnf(preRhs);
    if (!(lhs == preLhs && rhs == preRhs)) {
      var result = compareApprox(lhs, rhs);
      if (result != null) return result;
    }

    Term result;
    if (rhs instanceof MetaCall || rhs instanceof MetaLitTerm)
      result = swapped(() -> doCompareUntyped(rhs, lhs));
    else result = doCompareUntyped(lhs, rhs);
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
        if (!(rhs instanceof AppTerm(var g, var b))) yield null;
        var fTy = compareUntyped(f, g);
        if (!(fTy instanceof PiTerm pi)) yield null;
        if (!compare(a, b, pi.param())) yield null;
        yield pi.body().apply(a);
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
        if (!subscoped(() -> {
          var var = putIndex(DimTyTerm.INSTANCE);
          return compare(coe.type().apply(var), rType.apply(var), null);
        })) yield null;
        yield coe.family();
      }
      case ProjTerm(var lof, var ldx) -> {
        // Since {lhs} and {rhs} are whnf, at this point, {lof} is unable to evaluate.
        // Thus the only thing we can do is check whether {lof} and {rhs.of(}} (if rhs is ProjTerm) are 'the same'.
        if (!(rhs instanceof ProjTerm(var rof, var rdx))) yield null;
        if (!(compareUntyped(lof, rof) instanceof SigmaTerm(var params))) yield null;
        if (ldx != rdx) yield null;
        // Make type
        var spine = ImmutableSeq.fill(ldx /* ldx is 0-based */, i -> ProjTerm.make(lof, i));    // 0 = lof.0, 1 = lof.1, ...
        // however, for {lof.ldx}, the nearest(0) element is {lof.(idx - 1)}, so we need to reverse the spine.
        yield params.get(ldx).instantiateTele(spine.view());
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
        case ConCallLike rCon -> compareCallApprox(lCon, rCon, lCon.ref());
        default -> null;
      };
      case MetaLitTerm mlt -> switch (rhs) {
        case IntegerTerm mrt -> compareMetaLitWithLit(mlt, mrt.repr(), mrt.type());
        case ListTerm mrt -> compareMetaLitWithLit(mlt, mrt.repr(), mrt.type());
        case ConCall _ -> throw new UnsupportedOperationException("TODO (I have no time to implement this)");
        case MetaLitTerm mrt -> compareMetaLitWithLit(mlt, mrt.repr(), mrt.type());
        default -> null;
      };
      // We already compare arguments in compareApprox, if we arrive here,
      // it means their arguments don't match (even the ref don't match),
      // so we are unable to do more if we can't normalize them.
      case FnCall _ -> null;

      default -> throw noRules(lhs);
    };
  }

  private @Nullable Term compareMetaLitWithLit(@NotNull MetaLitTerm lhs, Object repr, @NotNull Term rhsType) {
    if (!Objects.equals(lhs.repr(), repr)) return null;
    if (compare(lhs.type(), rhsType, null)) return lhs.type();
    return null;
  }

  /** Compare {@param lambda} and {@param rhs} with {@param type} */
  private boolean compareLambda(@NotNull LamTerm lambda, @NotNull Term rhs, @NotNull PiTerm type) {
    return subscoped(() -> {
      var var = putIndex(type.param());
      var lhsBody = lambda.body().apply(var);
      var rhsBody = AppTerm.make(rhs, new FreeTerm(var));
      return compare(lhsBody, rhsBody, type.body().apply(var));
    });
  }

  private boolean compareLambda(@NotNull LamTerm lambda, @NotNull Term rhs, @NotNull EqTerm type) {
    return subscoped(() -> {
      var var = putIndex(DimTyTerm.INSTANCE);
      var lhsBody = lambda.body().apply(var);
      var free = new FreeTerm(var);
      var rhsBody = AppTerm.make(rhs, free);
      return compare(lhsBody, rhsBody, type.appA(free));
    });
  }

  private @Nullable Term compareMany(
    @NotNull ImmutableSeq<Term> list,
    @NotNull ImmutableSeq<Term> rist,
    int ulift, @NotNull JitTele types
  ) {
    assert list.sizeEquals(rist);
    assert rist.sizeEquals(types.telescopeSize);
    var argsCum = new Term[types.telescopeSize];

    for (var i = 0; i < list.size(); ++i) {
      var l = list.get(i);
      var r = rist.get(i);
      var ty = whnf(types.telescope(i, argsCum)).elevate(ulift);
      if (!compare(l, r, ty)) return null;
      argsCum[i] = l;
    }

    return whnf(types.result(argsCum));
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
    return subscoped(() -> {
      var name = putParam(new Param(nameGen.next(whnf(lTy)), lTy, true));
      return continuation.apply(name);
    });
  }

  private <R> R compareTypesWithAux(
    @NotNull SeqView<LocalVar> vars,
    @NotNull ImmutableSeq<Term> list,
    @NotNull ImmutableSeq<Term> rist,
    @NotNull Supplier<R> onFailed,
    @NotNull Function<ImmutableSeq<LocalVar>, R> continuation
  ) {
    if (!list.sizeEquals(rist)) return onFailed.get();
    if (list.isEmpty()) return continuation.apply(vars.toImmutableSeq());
    return compareTypeWith(
      list.getFirst().instantiateTeleVar(vars),
      rist.getFirst().instantiateTeleVar(vars), onFailed, var ->
        compareTypesWithAux(vars.appended(var), list.drop(1), rist.drop(1), onFailed, continuation));
  }

  /**
   * Compare types and run the {@param continuation} with those types in context (reverse order).
   *
   * @param onFailed     run while failed (size doesn't match or compare failed)
   * @param continuation a function that accept the {@link LocalVar} of all {@param list}
   */
  private <R> R compareTypesWith(
    @NotNull ImmutableSeq<Term> list,
    @NotNull ImmutableSeq<Term> rist,
    @NotNull Supplier<R> onFailed,
    @NotNull Function<ImmutableSeq<LocalVar>, R> continuation
  ) {
    return subscoped(() -> compareTypesWithAux(SeqView.empty(), list, rist, onFailed, continuation));
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
      case Pair(DataCall lhs, DataCall rhs) -> {
        if (!lhs.ref().equals(rhs.ref())) yield false;
        yield compareMany(lhs.args(), rhs.args(), lhs.ulift(), TyckDef.defSignature(lhs.ref())) != null;
      }
      case Pair(DimTyTerm _, DimTyTerm _) -> true;
      case Pair(PiTerm(var lParam, var lBody), PiTerm(var rParam, var rBody)) -> compareTypeWith(lParam, rParam,
        () -> false, var -> compare(lBody.apply(var), rBody.apply(var), null));
      case Pair(SigmaTerm(var lParams), SigmaTerm(var rParams)) ->
        compareTypesWith(lParams, rParams, () -> false, _ -> true);
      case Pair(SortTerm lhs, SortTerm rhs) -> compareSort(lhs, rhs);
      case Pair(EqTerm(var A, var a0, var a1), EqTerm(var B, var b0, var b1)) -> {
        var tyResult = subscoped(() -> {
          var var = putIndex(DimTyTerm.INSTANCE);
          return compare(A.apply(var), B.apply(var), null);
        });
        if (!tyResult) yield false;
        yield compare(a0, b0, A.apply(DimTerm.I0)) && compare(a1, b1, A.apply(DimTerm.I1));
      }
      default -> throw noRules(preLhs);
    };
  }

  private @NotNull LocalVar putParam(@NotNull Param param) {
    var var = LocalVar.generate(param.name());
    localCtx().put(var, param.type());
    return var;
  }

  public @NotNull LocalVar putIndex(@NotNull Term term) {
    return super.putIndex(nameGen, term);
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
    return compare(eqn.lhs(), eqn.rhs(), null);
  }
}
