// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.generic.stmt;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import kala.function.IndexedFunction;
import org.aya.generic.AyaDocile;
import org.aya.syntax.core.def.AnyDef;
import org.aya.syntax.core.def.ConDef;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.DataCall;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiPredicate;
import java.util.function.IntUnaryOperator;

/**
 * <h2> What should I do after I creating a new Shape? </h2>
 * <ul>
 *   <li>impl your Shape, see {@link org.aya.syntax.core.term.repr.IntegerTerm},
 *   and do everything you should after you creating a {@link Term}/{@link org.aya.syntax.core.pat.Pat}.</l1>
 *   <li>impl TermComparator, see {@code TermComparator.doCompareUntyped(Term, Term)}</li>
 *   <li>impl PatMatcher, see {@code PatMatcher#match(Pat, Term)}</li>
 *   <li>impl PatUnifier, see {@link org.aya.syntax.core.pat.PatToTerm}</li>
 * </ul>
 *
 * @param <T>
 */
public interface Shaped<T> {
  @NotNull Term type();

  sealed interface Inductive<T> extends Shaped<T> {
    @Override @NotNull DataCall type();
    /** Usually called for patterns, not terms, so terms can implement this as identity */
    @NotNull T constructorForm();
  }

  non-sealed interface Nat<T extends AyaDocile> extends Inductive<T> {
    @NotNull T makeZero();
    @NotNull T makeSuc(@NotNull T t);
    @NotNull T destruct(int repr);
    int repr();

    default @Override @NotNull T constructorForm() {
      int repr = repr();
      if (repr == 0) return makeZero();
      return makeSuc(destruct(repr - 1));
    }

    @NotNull Shaped.Nat<T> map(@NotNull IntUnaryOperator f);
  }

  non-sealed interface Bool<T extends AyaDocile> extends Inductive<T> {
    @NotNull T makeCon0();
    @NotNull T makeCon1();
    int repr();

    default @Override @NotNull T constructorForm() {
      int repr = repr();
      if (repr == 0) return makeCon0();
      return makeCon1();
    }
  }

  non-sealed interface List<T extends AyaDocile> extends Inductive<T> {
    @NotNull ImmutableSeq<T> repr();
    @NotNull T makeNil();
    @NotNull T makeCons(T x, T xs);
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
      return lhsRepr.sizeEquals(rhsRepr)
        && lhsRepr.allMatchWith(rhsRepr, comparator);
    }

    @Override default @NotNull T constructorForm() {
      var elements = repr();
      if (elements.isEmpty()) return makeNil();
      return makeCons(elements.getFirst(), destruct(elements.drop(1)));
    }
  }

  /**
   * Something Shaped which is applicable, like
   * {@link org.aya.syntax.core.def.FnDef}, {@link ConDef}, and probably {@link org.aya.syntax.core.def.DataDef}.
   * See also <code>RuleReducer</code>.
   */
  interface Applicable<Def extends AnyDef> extends Reducible {
    @NotNull Def ref();

    /**
     * Applying arguments.
     *
     * @param args arguments
     * @return null if failed
     */
    @Nullable Term apply(@NotNull ImmutableSeq<Term> args);
    @NotNull Applicable<Def> descent(@NotNull IndexedFunction<Term, Term> f);
    @Override default Term invoke(Term onStuck, @NotNull Seq<Term> args) {
      var result = apply(args.toImmutableSeq());
      if (result == null) return onStuck;
      return result;
    }
  }
}
