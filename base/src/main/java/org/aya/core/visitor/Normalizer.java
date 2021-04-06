// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.visitor;

import org.aya.api.util.NormalizeMode;
import org.aya.core.term.*;
import org.aya.util.Decision;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public final class Normalizer implements Unfolder<NormalizeMode> {
  public static final @NotNull Normalizer INSTANCE = new Normalizer();

  @Contract(pure = true) private Normalizer() {
  }

  @Override public @NotNull Term visitApp(@NotNull ElimTerm.App term, NormalizeMode mode) {
    var fn = term.fn();
    if (term.whnf() != Decision.NO) {
      if (mode != NormalizeMode.NF) return term;
      else return CallTerm.make(fn, visitArg(term.arg(), mode));
    }
    if (fn instanceof IntroTerm.Lambda lam) return CallTerm.make(lam, term.arg()).accept(this, mode);
    else return CallTerm.make(fn.accept(this, mode), term.arg()).accept(this, mode);
  }

  @Override public @NotNull Term visitRef(@NotNull RefTerm term, NormalizeMode mode) {
    return term;
  }

  @Override public @NotNull Term visitLam(@NotNull IntroTerm.Lambda term, NormalizeMode mode) {
    if (mode != NormalizeMode.NF) return term;
    else return Unfolder.super.visitLam(term, mode);
  }

  @Override public @NotNull Term visitPi(@NotNull FormTerm.Pi term, NormalizeMode mode) {
    if (mode != NormalizeMode.NF) return term;
    else return Unfolder.super.visitPi(term, mode);
  }

  @Override public @NotNull Term visitSigma(@NotNull FormTerm.Sigma term, NormalizeMode mode) {
    if (mode != NormalizeMode.NF) return term;
    else return Unfolder.super.visitSigma(term, mode);
  }

  @Override public @NotNull Term visitTup(@NotNull IntroTerm.Tuple term, NormalizeMode mode) {
    if (mode != NormalizeMode.NF) return term;
    else return Unfolder.super.visitTup(term, mode);
  }

  @Override public @NotNull Term visitProj(@NotNull ElimTerm.Proj term, NormalizeMode mode) {
    var tup = term.tup().accept(this, NormalizeMode.WHNF);
    var ix = term.ix();
    if (!(tup instanceof IntroTerm.Tuple t)) return new ElimTerm.Proj(tup, ix);
    // should not fail due to tycking
    assert ix <= t.items().size();
    assert ix > 0;
    return t.items().get(ix - 1).accept(this, mode);
  }
}
