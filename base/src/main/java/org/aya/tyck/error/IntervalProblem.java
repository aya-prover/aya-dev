// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import org.aya.concrete.Expr;
import org.aya.core.term.FormTerm;
import org.aya.core.term.Term;
import org.aya.generic.ExprProblem;
import org.aya.pretty.doc.Doc;
import org.aya.util.distill.DistillerOptions;
import org.jetbrains.annotations.NotNull;

public sealed interface IntervalProblem extends ExprProblem {
  @Override default @NotNull Severity level() {
    return Severity.ERROR;
  }

  record UnknownInterval(@NotNull Expr expr) implements IntervalProblem {
    @Override
    public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.vcat(
        Doc.english("Unable to check the expression"),
        Doc.par(1, expr.toDoc(options)),
        Doc.english("of interval type")
      );
    }
  }
}
