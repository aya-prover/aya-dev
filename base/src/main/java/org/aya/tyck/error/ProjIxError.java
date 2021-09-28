// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.error;

import org.aya.api.error.ExprProblem;
import org.aya.concrete.Expr;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

public record ProjIxError(@Override @NotNull Expr.ProjExpr expr, int actual, int expectedBound) implements ExprProblem {
  @Override public @NotNull Doc describe() {
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
