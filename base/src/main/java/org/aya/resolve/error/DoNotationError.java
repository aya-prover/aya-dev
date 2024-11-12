// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.error;

import org.aya.pretty.doc.Doc;
import org.aya.syntax.concrete.Expr;
import org.aya.tyck.error.SourceNodeProblem;
import org.aya.util.error.WithPos;
import org.aya.util.prettier.PrettierOptions;
import org.jetbrains.annotations.NotNull;

public record DoNotationError(
  @NotNull WithPos<Expr> expr
) implements SourceNodeProblem {
  @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
    return Doc.english("Last expression in a do block cannot be a bind expression");
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }
}
