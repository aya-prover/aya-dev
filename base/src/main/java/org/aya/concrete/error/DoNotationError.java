// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.error;

import org.aya.concrete.Expr;
import org.aya.generic.ExprProblem;
import org.aya.pretty.doc.Doc;
import org.aya.util.pretty.PrettierOptions;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

public record DoNotationError(
  @NotNull SourcePos sourcePos,
  @NotNull Expr expr
) implements ExprProblem {
  @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
    return Doc.english("Last expression in a do block cannot be a bind expression");
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }
}
