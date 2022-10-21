// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.generic;

import kala.collection.immutable.ImmutableSeq;
import kala.function.TriFunction;
import org.aya.core.def.CtorDef;
import org.aya.core.pat.Pat;
import org.aya.core.repr.AyaShape;
import org.aya.core.term.CallTerm;
import org.aya.core.term.ErrorTerm;
import org.aya.core.term.Term;
import org.aya.generic.util.InternalException;
import org.aya.generic.util.NormalizeMode;
import org.aya.tyck.TyckState;
import org.aya.tyck.unify.TermComparator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * <h2> What should I do if I create a new Shape? </h2>
 * <ul>
 *   <l1>impl your Shape, see {@link org.aya.core.term.LitTerm.ShapedInt}, and do everything you should after you creating a Term.</l1>
 *   <li>impl TermComparator, see {@link TermComparator#doCompareUntyped(Term, Term, TermComparator.Sub, TermComparator.Sub)}</li>
 *   <li>impl PatMatcher, see {@link org.aya.core.pat.PatMatcher#match(Pat, Term)}</li>
 *   <li>impl PatUnifier, see {@link org.aya.core.pat.PatUnify#unify(Pat, Pat)}</li>
 * </ul>
 * @param <T>
 */
public interface Shaped<T> {
  @NotNull AyaShape shape();
  @NotNull Term type();
  @NotNull T constructorForm(@Nullable TyckState state);
  default @NotNull T constructorForm() {
    return constructorForm(null);
  }

  interface Nat<T> extends Shaped<T> {
    @Override @NotNull Term type();
    @NotNull T makeZero(@NotNull CtorDef zero);
    @NotNull T makeSuc(@NotNull CtorDef suc, @NotNull T t);
    @NotNull T destruct(int repr);
    int repr();

    default <O> boolean compareShape(@NotNull TyckState state, @NotNull Shaped<O> other) {
      if (shape() != other.shape()) return false;
      if (!(other instanceof Shaped.Nat<?> otherData)) return false;
      if (type().normalize(state, NormalizeMode.WHNF) instanceof CallTerm.Data lhs
        && otherData.type().normalize(state, NormalizeMode.WHNF) instanceof CallTerm.Data rhs) {
        return lhs.ref() == rhs.ref();
      } else return false;
    }

    /**
     * Presumption: {@code this} and {@code other} are well-typed terms of the same type.
     * This is true for {@link org.aya.core.pat.PatUnify} and {@link org.aya.core.pat.PatMatcher}.
     */
    default <O> boolean compareUntyped(@NotNull Shaped<O> other) {
      assert other instanceof Nat<?>;
      var otherData = (Nat<O>) other;
      return repr() == otherData.repr();
    }

    default @Override @NotNull T constructorForm(@Nullable TyckState state) {
      int repr = repr();
      return with(state, (zero, suc) -> {
        if (repr == 0) return makeZero(zero);
        return makeSuc(suc, destruct(repr - 1));
      }, () -> {
        // TODO[literal]: how to handle this?
        throw new InternalException("trying to make constructor form without type solved");
      });
    }

    default <R> R with(
      @NotNull BiFunction<CtorDef, CtorDef, R> block,
      @NotNull Supplier<R> unsolved
    ) {
      return with(null, block, unsolved);
    }

    default <R> R with(
      @Nullable TyckState state,
      @NotNull BiFunction<CtorDef, CtorDef, R> block,
      @NotNull Supplier<R> unsolved
    ) {
      var type = solved(state);
      if (type == null) return unsolved.get();
      var dataDef = type.ref().core;
      var zeroOpt = dataDef.body.find(it -> it.selfTele.sizeEquals(0));
      var sucOpt = dataDef.body.find(it -> it.selfTele.sizeEquals(1));
      if (zeroOpt.isEmpty() || sucOpt.isEmpty()) throw new InternalException("shape recognition bug");
      var zero = zeroOpt.get();
      var suc = sucOpt.get();
      return block.apply(zero, suc);
    }

    private @Nullable CallTerm.Data solved(@Nullable TyckState state) {
      var type = type();
      // already reported as UnsolvedMeta
      if (type instanceof ErrorTerm) return null;
      if (type instanceof CallTerm.Data data) return data;
      if (type instanceof CallTerm.Hole hole) {
        if (state == null) return null;
        var sol = findSolution(state, hole);
        if (sol instanceof CallTerm.Data data) return data;
        // report ill-typed solution? is this possible?
        throw new InternalException("unknown type for literal");
      }
      throw new InternalException("unknown type for literal");
    }

    private @Nullable Term findSolution(@NotNull TyckState state, @NotNull Term maybeHole) {
      if (maybeHole instanceof CallTerm.Hole hole) {
        var sol = state.metas().getOrNull(hole.ref());
        if (sol == null) return null;
        else return findSolution(state, sol);
      }
      return maybeHole;
    }
  }

  interface List<T> extends Shaped<T> {
    @NotNull ImmutableSeq<T> repr();
    @NotNull T makeNil(@NotNull CtorDef nil, @NotNull Arg<Term> type);
    @NotNull T makeCons(@NotNull CtorDef cons, @NotNull Arg<Term> type, T value, T last);
    @NotNull T destruct(@NotNull ImmutableSeq<T> repr);

    default <O> boolean compareShape(@NotNull TermComparator comparator, @NotNull Shaped<O> other) {
      if (shape() != other.shape()) return false;
      if (!(other instanceof Shaped.List<?> otherData)) return false;
      return comparator.compare(type(), otherData.type(), null);   // TODO[hoshino]: I don't know whether it is correct.
    }

    /**
     * Comparing two List
     *
     * @param other      another list
     * @param comparator a comparator that should compare the subterms between two List.
     * @return true if they matches (a term matches a pat or two terms are equivalent,
     * which depends on the type parameters {@link T} and {@link O}), false if otherwise.
     */
    default <O> boolean compareUntyped(@NotNull Shaped.List<O> other, @NotNull BiFunction<T, O, Boolean> comparator) {
      var lhsRepr = repr();
      var rhsRepr = other.repr();
      if (!lhsRepr.sizeEquals(rhsRepr)) return false;    // the size should equal.
      return lhsRepr.zip(rhsRepr)
        .foldLeft(true, (l, tuple) ->
          l && comparator.apply(tuple._1, tuple._2));
    }

    /// region Copied from Shaped.Nat
    /// FIXME: see above, maybe move these to Shaped.Data

    @Override
    default @NotNull T constructorForm(@Nullable TyckState state) {
      return with(state, (nil, cons, dataArg) -> {
        var elements = repr();
        if (elements.isEmpty()) return makeNil(nil, dataArg);
        return makeCons(cons, dataArg, elements.first(), destruct(elements.drop(1)));
      }, () -> {
        // TODO[literal]: how to handle this?
        throw new InternalException("trying to make constructor form without type solved");
      });
    }

    default <R> R with(
      @NotNull TriFunction<CtorDef, CtorDef, Arg<Term>, R> block,
      @NotNull Supplier<R> unsolved
    ) {
      return with(null, block, unsolved);
    }

    default <R> R with(
      @Nullable TyckState state,
      @NotNull TriFunction<CtorDef, CtorDef, Arg<Term>, R> block,
      @NotNull Supplier<R> unsolved
    ) {
      var type = solved(state);
      if (type == null) return unsolved.get();
      var dataDef = type.ref().core;
      var nilCtor = dataDef.body.find(it -> it.selfTele.sizeEquals(0));
      var consCtor = dataDef.body.find(it -> it.selfTele.sizeEquals(2));
      if (nilCtor.isEmpty() || consCtor.isEmpty()) throw new InternalException("shape recognition bug");
      var nil = nilCtor.get();
      var cons = consCtor.get();
      var dataArg = type.args().first();    // Check?
      return block.apply(nil, cons, dataArg);
    }

    private @Nullable CallTerm.Data solved(@Nullable TyckState state) {
      var type = type();
      // already reported as UnsolvedMeta
      if (type instanceof ErrorTerm) return null;
      if (type instanceof CallTerm.Data data) return data;
      if (type instanceof CallTerm.Hole hole) {
        if (state == null) return null;
        var sol = findSolution(state, hole);
        if (sol instanceof CallTerm.Data data) return data;
        // report ill-typed solution? is this possible?
        throw new InternalException("unknown type for literal");
      }
      throw new InternalException("unknown type for literal");
    }

    private @Nullable Term findSolution(@NotNull TyckState state, @NotNull Term maybeHole) {
      if (maybeHole instanceof CallTerm.Hole hole) {
        var sol = state.metas().getOrNull(hole.ref());
        if (sol == null) return null;
        else return findSolution(state, sol);
      }
      return maybeHole;
    }

    /// endregion
  }
}
