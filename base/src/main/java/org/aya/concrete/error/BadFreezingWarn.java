// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.error;

import org.aya.concrete.Expr;
import org.aya.generic.ExprProblem;
import org.aya.pretty.doc.Doc;
import org.aya.util.pretty.PrettierOptions;
import org.jetbrains.annotations.NotNull;

public record BadFreezingWarn(@NotNull Expr expr) implements ExprProblem {
  @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
    return Doc.english("Meaningless freeze condition in projection");
  }

  @Override public @NotNull Severity level() {
    return Severity.WARN;
  }
}
