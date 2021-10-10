// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import org.aya.api.distill.DistillerOptions;
import org.aya.api.error.ExprProblem;
import org.aya.concrete.Expr;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

public sealed interface UnivArgsError extends ExprProblem {
  @Override default @NotNull Severity level() {
    return Severity.ERROR;
  }

  record SizeMismatch(@NotNull Expr.UnivArgsExpr expr, int expected) implements UnivArgsError {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.sep(Doc.plain("Expected"), Doc.plain(String.valueOf(expected)),
        Doc.english("universe arguments, but"),
        Doc.plain(String.valueOf(expr.univArgs().size())),
        Doc.english("are provided"));
    }
  }

  record Misplaced(@NotNull Expr.UnivArgsExpr expr) implements UnivArgsError {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.english("Universe argument should not be placed here");
    }
  }
}
