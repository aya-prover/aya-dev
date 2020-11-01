// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.tyck.error;

import org.jetbrains.annotations.NotNull;
import org.mzi.concrete.Expr;
import org.mzi.core.term.AppTerm;

/**
 * @author ice1000
 */
public record HoleBadSpineError(@NotNull AppTerm.HoleApp term, @NotNull Expr expr) implements TyckProblem {
  @Override
  public @NotNull String describe() {
    return "Can't perform pattern unification on hole with spine " + term.args() + ".";
  }

  @Override public @NotNull Severity level() {
    return Severity.WARN;
  }
}
