// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.tycker;

import org.aya.normalize.Finalizer;
import org.aya.normalize.Normalizer;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.ref.MetaVar;
import org.aya.tyck.TyckState;
import org.jetbrains.annotations.NotNull;

/**
 * Indicating something is {@link TyckState}ful,
 * therefore we can perform weak-head normalizing and <b>Ice Spell 「 Perfect Freeze 」</b>
 *
 * @see #state()
 * @see #whnf(Term)
 * @see #freezeHoles(Term)
 * @see Contextful
 */
public interface Stateful {
  @NotNull TyckState state();
  default @NotNull Term whnf(@NotNull Term term) {
    return new Normalizer(state()).apply(term);
  }
  /**
   * Does not validate solution.
   */
  default void solve(MetaVar meta, Term solution) {
    state().solve(meta, solution);
  }
  default @NotNull Term freezeHoles(@NotNull Term term) {
    return new Finalizer.Freeze(this).zonk(term);
  }
}
