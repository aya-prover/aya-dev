// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import org.aya.concrete.Expr;
import org.aya.generic.ExprProblem;
import org.aya.pretty.doc.Doc;
import org.aya.util.distill.DistillerOptions;
import org.jetbrains.annotations.NotNull;

public record ProjIxError(@Override @NotNull Expr.ProjExpr expr, int actual, int expectedBound) implements ExprProblem {
  @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
    return Doc.sep(
      Doc.english("Cannot project the"),
      Doc.ordinal(actual),
      Doc.english("element because the type has index range"),
      Doc.plain("[1, " + expectedBound + "]")
    );
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }
}
