// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.tyck.error;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.error.Problem;
import org.mzi.concrete.Expr;
import org.mzi.core.term.AppTerm;
import org.mzi.pretty.doc.Doc;

/**
 * @author ice1000
 */
public record HoleAppWarn(@NotNull AppTerm.HoleApp term, @NotNull Expr expr) implements TyckProblem, Problem.Warn {
  @Override @Contract(" -> new")
  public @NotNull Doc describe() {
    return Doc.plain("Attempting to unify a hole applied with argument: `" + term.var().name() + "`," +
      "this is not supported by the naive unifier. Please use pattern unifier instead.");
  }
}
