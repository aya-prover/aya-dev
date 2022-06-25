// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import org.aya.core.term.*;
import org.aya.generic.util.NormalizeMode;
import org.aya.tyck.TyckState;
import org.aya.util.distill.DistillerOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record Normalizer(@Nullable TyckState state) implements Expander<NormalizeMode> {
  @Override public @NotNull Term visitApp(@NotNull ElimTerm.App term, NormalizeMode mode) {
    var fn = term.of().accept(this, mode);
    if (fn instanceof IntroTerm.Lambda lambda)
      return CallTerm.make(lambda, visitArg(term.arg(), mode)).accept(this, mode);
    if (mode == NormalizeMode.NF) // FIXME: in case it's not NF, reduce again
      return CallTerm.make(fn, visitArg(term.arg(), mode));
    else return term;
  }

  @Override public @NotNull Term visitRef(@NotNull RefTerm term, NormalizeMode mode) {
    return term;
  }

  @Override public @NotNull Term
  visitFieldRef(@NotNull RefTerm.Field term, NormalizeMode normalizeMode) {
    return term;
  }

  @Override public @NotNull Term visitMetaPat(@NotNull RefTerm.MetaPat metaPat, NormalizeMode normalizeMode) {
    return metaPat.inline();
  }

  @Override public @NotNull Term visitLam(@NotNull IntroTerm.Lambda term, NormalizeMode mode) {
    if (mode != NormalizeMode.NF) return term;
    else return Expander.super.visitLam(term, mode);
  }

  @Override public @NotNull Term visitPi(@NotNull FormTerm.Pi term, NormalizeMode mode) {
    if (mode != NormalizeMode.NF) return term;
    else return Expander.super.visitPi(term, mode);
  }

  @Override public @NotNull Term visitSigma(@NotNull FormTerm.Sigma term, NormalizeMode mode) {
    if (mode != NormalizeMode.NF) return term;
    else return Expander.super.visitSigma(term, mode);
  }

  @Override public @NotNull Term visitTup(@NotNull IntroTerm.Tuple term, NormalizeMode mode) {
    if (mode != NormalizeMode.NF) return term;
    else return Expander.super.visitTup(term, mode);
  }

  @Override public @NotNull Term visitProj(@NotNull ElimTerm.Proj term, NormalizeMode mode) {
    var tup = term.of().accept(this, NormalizeMode.WHNF);
    var ix = term.ix();
    if (!(tup instanceof IntroTerm.Tuple t)) return tup == term.of() ? term : new ElimTerm.Proj(tup, ix);
    // should not fail due to tycking
    assert t.items().sizeGreaterThanOrEquals(ix) && ix > 0 : term.toDoc(DistillerOptions.debug()).debugRender();
    return t.items().get(ix - 1).accept(this, mode);
  }

  @Override public @NotNull Term visitDataCall(CallTerm.@NotNull Data dataCall, NormalizeMode normalizeMode) {
    if (normalizeMode == NormalizeMode.WHNF) return dataCall;
    return Expander.super.visitDataCall(dataCall, normalizeMode);
  }
}
