// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.tyck.unify;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.util.NormalizeMode;
import org.mzi.core.term.AppTerm;
import org.mzi.core.term.HoleTerm;
import org.mzi.core.term.Term;
import org.mzi.tyck.error.HoleAppWarn;
import org.mzi.tyck.sort.LevelEqn;
import org.mzi.util.Decision;
import org.mzi.util.Ordering;

public class NaiveDefEq extends DefEq {
  protected NaiveDefEq(@NotNull Ordering ord, LevelEqn.@NotNull Set equations) {
    super(ord, equations);
  }

  @Override
  public @NotNull Boolean visitApp(@NotNull AppTerm.Apply lhs, @NotNull Term preRhs, @Nullable Term type) {
    if (lhs.fn() instanceof HoleTerm holeTerm) {
      equations.reporter().report(new HoleAppWarn(holeTerm, expr));
      return false;
    }
    if (lhs.whnf() == Decision.YES && preRhs instanceof AppTerm.Apply rhs)
      return compare(lhs.fn(), rhs.fn(), null) && compare(lhs.arg().term(), rhs.arg().term(), null);
    return lhs.normalize(NormalizeMode.WHNF).accept(this, preRhs, type);
  }
}
