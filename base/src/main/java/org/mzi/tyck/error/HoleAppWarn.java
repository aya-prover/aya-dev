// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.tyck.error;

import org.jetbrains.annotations.NotNull;
import org.mzi.concrete.Expr;
import org.mzi.core.term.HoleTerm;

/**
 * @author ice1000
 */
public record HoleAppWarn(@NotNull HoleTerm term, @NotNull Expr expr) implements TyckProblem {
  @Override public @NotNull Severity level() {
    return Severity.WARN;
  }

  @Override
  public @NotNull String describe() {
    return "Attempting to unify a hole applied with argument: `" + term.var() + "`," +
      "this is not supported by the naive unifier. Please use pattern unifier instead.";
  }
}
