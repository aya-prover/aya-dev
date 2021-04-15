// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.desugar.error;

import org.aya.api.error.ExprProblem;
import org.aya.concrete.Expr;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

public record BadLevelError(
  @NotNull Expr expr
) implements ExprProblem {
  @Override public @NotNull Doc describe() {
    return Doc.hcat(Doc.plain("Expected level expression, got: "), expr.toDoc());
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }
}
