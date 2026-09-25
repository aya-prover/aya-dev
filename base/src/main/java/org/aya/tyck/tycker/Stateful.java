// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.tycker;

import org.aya.generic.TermVisitor;
import org.aya.normalize.Finalizer;
import org.aya.normalize.Normalizer;
import org.aya.states.TyckState;
import org.aya.syntax.core.annotation.Closed;
import org.aya.syntax.core.term.ErrorTerm;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.xtt.CofNF;
import org.aya.syntax.core.term.xtt.DimTerm;
import org.aya.syntax.literate.CodeOptions;
import org.aya.syntax.ref.MetaVar;
import org.aya.util.ForLSP;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  default @Nullable CofNF.OrVar<CofNF.Disj> expand(@Closed @NotNull Term term) { return new Normalizer(state()).expand(term); }
  default @NotNull TermVisitor whnfVisitor() {
    return TermVisitor.expectTerm(this::whnf);
  }

  /// Does not validate solution.
  default void solve(MetaVar meta, Term solution) { state().solve(meta, solution); }
  default @Closed @NotNull Term freezeHoles(@Closed @NotNull Term term) { return new Finalizer.Freeze(this).zonk(term); }

  @ForLSP default @Closed @NotNull Term fullNormalize(@Closed @NotNull Term result) {
    return new Normalizer(state()).normalize(result, CodeOptions.NormalizeMode.FULL);
  }

  private void connectConj(@NotNull CofNF.OrVar<CofNF.Conj> cofOrVar) {
    switch (cofOrVar) {
      case CofNF.IsVar(var v) -> state().assume(v);
      case CofNF.Conc(var cof) -> {
        for (var eqcof : cof.elements())
          switch (eqcof) {
            case CofNF.IsVar(var v) -> state().assume(v);
            case CofNF.Conc(var c) -> state().connect(c.lhs(), c.rhs());
          }
      }
    }
  }

  private void disconnectConj(@NotNull CofNF.OrVar<CofNF.Conj> cofOrVar) {
    switch (cofOrVar) {
      case CofNF.IsVar(var v) -> state().unassume(v);
      case CofNF.Conc(var cof) -> {
        for (var eqcof : cof.elements())
          switch (eqcof) {
            case CofNF.IsVar(var v) -> state().unassume(v);
            case CofNF.Conc(var c) -> state().disconnect(c.lhs(), c.rhs());
          }
      }
    }
  }

  private <R> R withConjCof(@NotNull CofNF.OrVar<CofNF.Conj> cof, @NotNull Supplier<R> action, @NotNull Supplier<R> ifBottom) {
    connectConj(cof);
    var ret = state().isConnected(DimTerm.I0, DimTerm.I1) ? ifBottom.get() : action.get();
    disconnectConj(cof);
    return ret;
  }

  default <T> T withConnection(@NotNull CofNF.OrVar<CofNF.Disj> cofOrVar, @NotNull Supplier<T> action, @NotNull Supplier<T> ifBottom) {
    return switch (cofOrVar) {
      case CofNF.Conc(var cof) -> {
        T ret = null;
        for (var conj : cof.elements()) {
          ret = withConjCof(conj, action, ifBottom);
          if (ret instanceof ErrorTerm) {
            yield ret;
          }
        }
        yield ret == null ? ifBottom.get() : ret;
      }
      case CofNF.IsVar(var v) -> {
        state().assume(v);
        var ret = action.get();
        state().unassume(v);
        yield ret;
      }
    };
  }

  default boolean withConnection(@NotNull CofNF.OrVar<CofNF.Disj> cof, @NotNull Supplier<Boolean> action) {
    return withConnection(cof, action, () -> true);
  }
}
