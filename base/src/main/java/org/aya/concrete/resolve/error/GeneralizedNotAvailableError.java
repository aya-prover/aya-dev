// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.resolve.error;

import org.aya.api.distill.DistillerOptions;
import org.aya.api.error.ExprProblem;
import org.aya.concrete.Expr;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.jetbrains.annotations.NotNull;

public record GeneralizedNotAvailableError(@Override @NotNull Expr expr) implements ExprProblem, ResolveProblem {
  @Override public @NotNull Doc describe() {
    return Doc.sep(
      Doc.english("The generalized variable"),
      Doc.styled(Style.code(), expr.toDoc(DistillerOptions.DEFAULT)),
      Doc.english("is not available here")
    );
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }
}
