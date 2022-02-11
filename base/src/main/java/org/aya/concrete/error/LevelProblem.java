// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.error;

import org.aya.concrete.Expr;
import org.aya.generic.ExprProblem;
import org.aya.pretty.doc.Doc;
import org.aya.util.distill.DistillerOptions;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public sealed interface LevelProblem extends ExprProblem {
  @Override default @NotNull Severity level() {
    return Severity.ERROR;
  }

  record BadTypeExpr(@Override @NotNull Expr.AppExpr expr, int expected)
    implements LevelProblem {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.english("Expected " + expected + " level(s)");
    }
  }

  record BadLevelExpr(@Override @NotNull Expr expr) implements LevelProblem {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.sep(Doc.english("Expected level expression, got:"), expr.toDoc(options));
    }
  }
}
