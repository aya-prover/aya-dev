// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.core.visitor;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.util.NormalizeMode;
import org.mzi.core.term.*;
import org.mzi.util.Decision;

public final class NormalizeFixpoint implements UnfoldFixpoint<NormalizeMode> {
  public static final @NotNull NormalizeFixpoint INSTANCE = new NormalizeFixpoint();

  @Contract(pure = true) private NormalizeFixpoint() {
  }

  @Override
  public @NotNull Term visitApp(AppTerm.@NotNull Apply term, NormalizeMode mode) {
    var fn = term.fn();
    if (term.whnf() != Decision.NO) {
      if (mode != NormalizeMode.NF) return term;
      else return AppTerm.make(fn, visitArg(term.arg(), mode));
    }
    if (fn instanceof LamTerm lam) return AppTerm.make(lam, term.arg()).accept(this, mode);
    else return AppTerm.make(fn.accept(this, mode), term.arg()).accept(this, mode);
  }

  @Override
  public @NotNull Term visitRef(@NotNull RefTerm term, NormalizeMode mode) {
    return term;
  }

  @Override
  public @NotNull Term visitLam(@NotNull LamTerm term, NormalizeMode mode) {
    if (mode != NormalizeMode.NF) return term;
    else return UnfoldFixpoint.super.visitLam(term, mode);
  }

  @Override
  public @NotNull Term visitDT(@NotNull DT term, NormalizeMode mode) {
    if (mode != NormalizeMode.NF) return term;
    else return UnfoldFixpoint.super.visitDT(term, mode);
  }

  @Override
  public @NotNull Term visitTup(@NotNull TupTerm term, NormalizeMode mode) {
    if (mode != NormalizeMode.NF) return term;
    else return UnfoldFixpoint.super.visitTup(term, mode);
  }

  @Override
  public @NotNull Term visitProj(@NotNull ProjTerm term, NormalizeMode mode) {
    var tup = term.tup().accept(this, NormalizeMode.WHNF);
    var ix = term.ix();
    if (!(tup instanceof TupTerm t)) return new ProjTerm(tup, ix);
    // should not fail due to tycking
    assert ix <= t.items().size();
    assert ix > 0;
    return t.items().get(ix - 1).accept(this, mode);
  }
}
