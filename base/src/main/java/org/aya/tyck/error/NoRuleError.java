// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import org.aya.concrete.Expr;
import org.aya.core.term.Term;
import org.aya.generic.ExprProblem;
import org.aya.pretty.doc.Doc;
import org.aya.util.pretty.PrettierOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record NoRuleError(@Override @NotNull Expr expr, @Nullable Term term) implements ExprProblem, TyckError {
  @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
    if (term != null)
      return Doc.sep(Doc.english("No rule for checking the expression"),
        expr.toDoc(options),
        Doc.english("against the type"),
        term.toDoc(options));
    else
      return Doc.sep(Doc.english("No rule inferring the type of"), expr.toDoc(options));
  }
}
