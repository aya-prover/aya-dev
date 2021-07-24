// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.desugar.error;

import org.aya.api.error.ExprProblem;
import org.aya.api.ref.LevelGenVar;
import org.aya.concrete.Expr;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public sealed interface LevelProblem extends ExprProblem {
  @Override default @NotNull Severity level() {
    return Severity.ERROR;
  }

  record BadTypeExpr(@NotNull Expr.AppExpr expr, int expected)
    implements LevelProblem {
    @Override public @NotNull Doc describe() {
      return Doc.english("Expected " + expected + " level(s)");
    }
  }

  record BadLevelExpr(
    @NotNull Expr expr
  ) implements LevelProblem {
    @Override public @NotNull Doc describe() {
      return Doc.fillSep(Doc.english("Expected level expression, got:"), expr.toDoc());
    }
  }

  record BadLevelKind(
    @NotNull Expr expr, @NotNull LevelGenVar.Kind kind
  ) implements LevelProblem {
    @Override public @NotNull Doc describe() {
      return Doc.english("I don't want a " + kind.keyword + " here, please use the other one");
    }
  }
}
