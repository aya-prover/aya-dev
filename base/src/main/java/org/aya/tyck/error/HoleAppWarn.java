// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.error;

import org.aya.api.error.Problem;
import org.aya.concrete.Expr;
import org.aya.core.term.AppTerm;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public record HoleAppWarn(
  @NotNull AppTerm.HoleApp term,
  @NotNull Expr expr
) implements TyckProblem, Problem.Warn {
  @Override @Contract(" -> new")
  public @NotNull Doc describe() {
    return Doc.plain("Attempting to unify a hole applied with argument: `" + term.var().name() + "`," +
      "this is not supported by the naive unifier. Please use pattern unifier instead.");
  }
}
