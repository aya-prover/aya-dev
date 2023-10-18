// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.generic;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.stmt.decl.TeleDecl;
import org.aya.core.def.CtorDef;
import org.aya.core.def.Def;
import org.aya.core.pat.Pat;
import org.aya.core.repr.CodeShape;
import org.aya.core.repr.ShapeRecognition;
import org.aya.core.term.*;
import org.aya.ref.DefVar;
import org.aya.tyck.unify.TermComparator;
import org.aya.util.Arg;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiPredicate;

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
public interface Shaped<T> {
  @NotNull Term type();

  sealed interface Inductive<T> extends Shaped<T> {
    @Override @NotNull DataCall type();
    @NotNull ShapeRecognition recognition();
    @NotNull T constructorForm();

    @SuppressWarnings("unchecked") default @NotNull DefVar<CtorDef, ?> ctorRef(@NotNull CodeShape.GlobalId id) {
      return (DefVar<CtorDef, ?>) recognition().captures().get(id);
    }

    default <O> boolean compareShape(TermComparator comparator, @NotNull Inductive<O> other) {
      if (recognition().shape() != other.recognition().shape()) return false;
      if (other.getClass() != getClass()) return false;
      return comparator.compare(type(), other.type(), null);
    }
  }

  non-sealed interface Nat<T extends AyaDocile> extends Inductive<T> {
    @NotNull T makeZero(@NotNull CtorDef zero);
    @NotNull T makeSuc(@NotNull CtorDef suc, @NotNull Arg<T> t);
    @NotNull T destruct(int repr);
    int repr();

    /** Untyped: compare the internal representation only */
    default <O extends AyaDocile> boolean compareUntyped(@NotNull Shaped.Nat<O> other) {
      return repr() == other.repr();
    }

    default @Override @NotNull T constructorForm() {
      int repr = repr();
      var zero = ctorRef(CodeShape.GlobalId.ZERO);
      var suc = ctorRef(CodeShape.GlobalId.SUC);
      if (repr == 0) return makeZero(zero.core);
      return makeSuc(suc.core, new Arg<>(destruct(repr - 1), true));
    }

    // int construct(@NotNull T term);
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
      return lhsRepr.sizeEquals(rhsRepr)
        && lhsRepr.allMatchWith(rhsRepr, comparator);
    }

    @Override default @NotNull T constructorForm() {
      var nil = ctorRef(CodeShape.GlobalId.NIL).core;
      var cons = ctorRef(CodeShape.GlobalId.CONS).core;
      var dataArg = type().args().getFirst(); // Check?
      var xLicit = cons.selfTele.get(0).explicit();
      var xsLicit = cons.selfTele.get(1).explicit();
      var elements = repr();
      if (elements.isEmpty()) return makeNil(nil, dataArg);
      return makeCons(cons, dataArg, new Arg<>(elements.getFirst(), xLicit),
        new Arg<>(destruct(elements.drop(1)), xsLicit));
    }
  }

  /**
   * Something Shaped which is appliable, like
   * {@link org.aya.core.def.FnDef}, {@link CtorDef}, and probably {@link org.aya.core.def.DataDef}
   *
   * @see ReduceRule.Fn
   */
  interface Appliable<T extends AyaDocile, Core extends Def, Concrete extends TeleDecl<?>> extends Shaped<T> {
    /**
     * The underlying ref
     */
    @NotNull DefVar<Core, Concrete> ref();

    /**
     * Applying arguments.
     *
     * @param args arguments
     * @return null if failed
     */
    @Nullable T apply(@NotNull ImmutableSeq<Arg<T>> args);
  }
}
