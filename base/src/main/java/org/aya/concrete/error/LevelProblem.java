// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.error;

import org.aya.concrete.Expr;
import org.aya.generic.ExprProblem;
import org.aya.pretty.doc.Doc;
import org.aya.util.prettier.PrettierOptions;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public sealed interface LevelProblem extends ExprProblem {
  @Override default @NotNull Severity level() {return Severity.ERROR;}
  record BadLevelExpr(@Override @NotNull Expr expr) implements LevelProblem {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(Doc.english("Expected level expression, got:"), expr.toDoc(options));
    }
  }
}
