// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.resolve.error;

import org.aya.api.error.ExprProblem;
import org.aya.concrete.Expr;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

public record GeneralizedNotAvailableError(@NotNull Expr expr) implements ExprProblem {
  @Override public @NotNull Doc describe() {
    return Doc.hcat(
      Doc.plain("Generalized variable `"),
      expr.toDoc(),
      Doc.plain("` not available here")
    );
  }

  @Override public @NotNull Stage stage() {
    return Stage.RESOLVE;
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }
}
