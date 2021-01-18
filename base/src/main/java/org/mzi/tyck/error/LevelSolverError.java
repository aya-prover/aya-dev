// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.tyck.error;

import org.glavo.kala.collection.Collection;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.error.Problem;
import org.mzi.concrete.Expr;
import org.mzi.pretty.doc.Doc;
import org.mzi.tyck.sort.LevelEqn;

/**
 * @author ice1000
 */
public record LevelSolverError(
  @NotNull Expr expr,
  @NotNull Collection<? extends LevelEqn<?>> eqn
) implements TyckProblem, Problem.Error {
  @Override public @NotNull Doc describe() {
    // TODO[ice]: improve this
    return Doc.plain("Cannot solve equation: " + eqn);
  }
}
