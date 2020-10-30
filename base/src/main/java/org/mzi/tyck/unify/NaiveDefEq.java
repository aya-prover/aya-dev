// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.tyck.unify;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.core.term.AppTerm;
import org.mzi.core.term.Term;
import org.mzi.tyck.error.HoleAppWarn;
import org.mzi.tyck.sort.LevelEqn;
import org.mzi.util.Ordering;

/**
 * @author ice1000
 */
public class NaiveDefEq extends DefEq {
  protected NaiveDefEq(@NotNull Ordering ord, LevelEqn.@NotNull Set equations) {
    super(ord, equations);
  }

  @Override
  public @NotNull Boolean visitHole(AppTerm.@NotNull HoleApp lhs, @NotNull Term preRhs, @Nullable Term type) {
    if (!lhs.args().isEmpty()) {
      equations.reporter().report(new HoleAppWarn(lhs, expr));
      return false;
    }
    if (lhs.solution().isDefined()) return compare(lhs.solution().get(), preRhs, type);
    return preRhs instanceof AppTerm.HoleApp rhs && lhs.var() == rhs.var();
  }
}
