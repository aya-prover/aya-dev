// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.tyck.error;

import asia.kala.collection.Collection;
import org.jetbrains.annotations.NotNull;
import org.mzi.concrete.Expr;
import org.mzi.tyck.sort.LevelEqn;

/**
 * @author ice1000
 */
public record LevelSolverError(
  @NotNull Expr expr,
  @NotNull Collection<? extends LevelEqn<?>> eqn
) implements TyckProblem {
  @Override public @NotNull String describe() {
    // TODO[ice]: improve this
    return "Cannot solve equation: " + eqn;
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }
}
