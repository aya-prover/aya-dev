// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.error;

import org.aya.api.error.ExprProblem;
import org.aya.concrete.Expr;
import org.aya.pretty.doc.Doc;
import org.aya.tyck.sort.LevelEqn;
import org.glavo.kala.collection.Collection;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public record LevelSolverError(
  @NotNull Expr expr,
  @NotNull Collection<? extends LevelEqn> eqn
) implements ExprProblem {
  @Override public @NotNull Doc describe() {
    // TODO[ice]: improve this
    return Doc.plain("Cannot solve equation: " + eqn);
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }
}
