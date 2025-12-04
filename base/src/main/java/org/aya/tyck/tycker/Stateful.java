// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.tycker;

import org.aya.generic.TermVisitor;
import org.aya.normalize.Finalizer;
import org.aya.normalize.Normalizer;
import org.aya.states.TyckState;
import org.aya.syntax.core.annotation.Closed;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.xtt.ConjCofNF;
import org.aya.syntax.core.term.xtt.DimTerm;
import org.aya.syntax.literate.CodeOptions;
import org.aya.syntax.ref.MetaVar;
import org.aya.util.ForLSP;
import org.jetbrains.annotations.NotNull;

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
  default @Closed @NotNull Term whnf(@Closed @NotNull Term term) { return new Normalizer(state()).apply(term); }
  default @NotNull TermVisitor whnfVisitor() {
    return TermVisitor.expectTerm(this::whnf);
  }

  /// Does not validate solution.
  default void solve(MetaVar meta, Term solution) { state().solve(meta, solution); }
  default @Closed @NotNull Term freezeHoles(@Closed @NotNull Term term) { return new Finalizer.Freeze(this).zonk(term); }

  @ForLSP default @Closed @NotNull Term fullNormalize(@Closed @NotNull Term result) {
    return new Normalizer(state()).normalize(result, CodeOptions.NormalizeMode.FULL);
  }

  private void connectConj(@NotNull ConjCofNF cof) {
    for (var eqcof : cof.elements()) {
      state().connect(eqcof.lhs(), eqcof.rhs());
    }
  }

  private void disconnectConj(@NotNull ConjCofNF cof) {
    for (var eqcof : cof.elements()) {
      state().disconnect(eqcof.lhs(), eqcof.rhs());
    }
  }

  default <R> R withConnection(@NotNull ConjCofNF cof, @NotNull Supplier<R> action, @NotNull Supplier<R> ifBottom) {
    connectConj(cof);
    var ret = state().isConnected(DimTerm.I0, DimTerm.I1) ? ifBottom.get() : action.get();
    disconnectConj(cof);
    return ret;
  }
}
