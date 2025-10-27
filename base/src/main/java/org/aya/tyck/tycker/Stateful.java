// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.tycker;

import org.aya.normalize.Finalizer;
import org.aya.normalize.Normalizer;
import org.aya.states.TyckState;
import org.aya.syntax.core.annotation.Closed;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.literate.CodeOptions;
import org.aya.syntax.ref.MetaVar;
import org.aya.util.ForLSP;
import org.jetbrains.annotations.NotNull;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Indicating something is {@link TyckState}ful,
 * therefore we can perform weak-head normalizing and <b>Freeze Spell 「 Perfect Freeze 」</b>
 *
 * @see #state()
 * @see #whnf(Term)
 * @see #freezeHoles(Term)
 * @see Contextful
 */
public interface Stateful {
  @NotNull TyckState state();
  default @NotNull @Closed Term whnf(@NotNull @Closed Term term) { return new Normalizer(state()).apply(term); }
  /// Does not validate solution.
  default void solve(MetaVar meta, Term solution) { state().solve(meta, solution); }
  default @NotNull Term freezeHoles(@NotNull Term term) { return new Finalizer.Freeze(this).zonk(term); }

  @ForLSP default @NotNull Term fullNormalize(Term result) {
    return new Normalizer(state()).normalize(result, CodeOptions.NormalizeMode.FULL);
  }

  default <R> R withConnection(@NotNull Term lhs, @NotNull Term rhs, @NotNull Supplier<R> action) {
    state().connect(lhs, rhs);
    var result = action.get();
    state().disconnect(lhs, rhs);
    return result;
  }

  /// Used too often, make a specialized version
  default boolean withConnection(@NotNull Term lhs, @NotNull Term rhs, @NotNull BooleanSupplier action) {
    state().connect(lhs, rhs);
    var result = action.getAsBoolean();
    state().disconnect(lhs, rhs);
    return result;
  }
}
