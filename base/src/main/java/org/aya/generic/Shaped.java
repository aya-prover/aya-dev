// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.generic;

import kala.collection.immutable.ImmutableSeq;
import kala.function.TriFunction;
import org.aya.core.def.CtorDef;
import org.aya.core.pat.Pat;
import org.aya.core.repr.AyaShape;
import org.aya.core.term.*;
import org.aya.generic.util.InternalException;
import org.aya.generic.util.NormalizeMode;
import org.aya.tyck.TyckState;
import org.aya.tyck.unify.TermComparator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

/**
 * <h2> What should I do after I creating a new Shape? </h2>
 * <ul>
 *   <li>impl your Shape, see {@link IntegerTerm},
 *   and do everything you should after you creating a {@link Term}/{@link Pat}.</l1>
 *   <li>impl TermComparator, see {@link TermComparator#doCompareUntyped(Term, Term, TermComparator.Sub, TermComparator.Sub)}</li>
 *   <li>impl PatMatcher, see {@link org.aya.core.pat.PatMatcher#match(Pat, Term)}</li>
 *   <li>impl PatUnifier, see {@link org.aya.core.pat.PatUnify#unify(Pat, Pat)}</li>
 * </ul>
 *
 * @param <T>
 */
@SuppressWarnings("JavadocReference")
public interface Shaped<T> {
  @NotNull AyaShape shape();
  @NotNull Term type();
  @NotNull T constructorForm(@Nullable TyckState state);
  default @NotNull T constructorForm() {
    return constructorForm(null);
  }

  sealed interface Inductive<T> extends Shaped<T> {
    @Override @NotNull Term type();

    default @Nullable DataCall solved(@Nullable TyckState state) {
      var type = type();
      if (state != null) type = type.normalize(state, NormalizeMode.WHNF);
      // already reported as UnsolvedMeta
      if (type instanceof ErrorTerm || type instanceof MetaTerm) return null;
      if (type instanceof DataCall data) return data;
      throw new InternalException("unknown type for literal of type " + type);
    }

    default <O> boolean compareShape(TermComparator comparator, @NotNull Shaped<O> other) {
      if (shape() != other.shape()) return false;
      if (other.getClass() != getClass()) return false;
      return comparator.compare(type(), other.type(), null);
    }
  }

  non-sealed interface Nat<T extends AyaDocile> extends Inductive<T> {
    @NotNull T makeZero(@NotNull CtorDef zero);
    @NotNull T makeSuc(@NotNull CtorDef suc, @NotNull Arg<T> t);
    @NotNull T destruct(int repr);
    int repr();

    /**
     * Presumption: {@code this} and {@code other} are well-typed terms of the same type.
     * This is true for {@link org.aya.core.pat.PatUnify} and {@link org.aya.core.pat.PatMatcher}.
     */
    default <O extends AyaDocile> boolean compareUntyped(@NotNull Shaped<O> other) {
      assert other instanceof Nat<?>;
      var otherData = (Nat<O>) other;
      return repr() == otherData.repr();
    }

    default @Override @NotNull T constructorForm(@Nullable TyckState state) {
      int repr = repr();
      return with(state, (zero, suc) -> {
        if (repr == 0) return makeZero(zero);
        return makeSuc(suc, new Arg<>(destruct(repr - 1), true));
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
  }

  non-sealed interface List<T extends AyaDocile> extends Inductive<T> {
    @NotNull ImmutableSeq<T> repr();
    @NotNull T makeNil(@NotNull CtorDef nil, @NotNull Arg<Term> type);
    @NotNull T makeCons(@NotNull CtorDef cons, @NotNull Arg<Term> type, Arg<T> x, Arg<T> xs);
    @NotNull T destruct(@NotNull ImmutableSeq<T> repr);

    /**
     * Comparing two List
     *
     * @param other      another list
     * @param comparator a comparator that should compare the subterms between two List.
     * @return true if they match (a term matches a pat or two terms are equivalent,
     * which depends on the type parameters {@link T} and {@link O}), false if otherwise.
     */
    default <O extends AyaDocile> boolean compareUntyped(@NotNull Shaped.List<O> other, @NotNull BiPredicate<T, O> comparator) {
      var lhsRepr = repr();
      var rhsRepr = other.repr();
      // the size should equal.
      if (!lhsRepr.sizeEquals(rhsRepr)) return false;
      return lhsRepr.allMatchWith(rhsRepr, comparator);
    }

    /// region Copied from Shaped.Nat
    /// FIXME: see above, maybe move these to Shaped.Data

    @Override
    default @NotNull T constructorForm(@Nullable TyckState state) {
      return with(state, (nil, cons, dataArg) -> {
        var xLicit = cons.selfTele.get(0).explicit();
        var xsLicit = cons.selfTele.get(1).explicit();
        var elements = repr();
        if (elements.isEmpty()) return makeNil(nil, dataArg);
        return makeCons(cons, dataArg, new Arg<>(elements.first(), xLicit),
          new Arg<>(destruct(elements.drop(1)), xsLicit));
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
    /// endregion
  }
}
