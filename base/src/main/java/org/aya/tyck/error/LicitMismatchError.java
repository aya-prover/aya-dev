// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.error;

import org.aya.api.distill.DistillerOptions;
import org.aya.api.error.ExprProblem;
import org.aya.concrete.Expr;
import org.aya.core.term.Term;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

public record LicitMismatchError(
  @NotNull Expr expr,
  @NotNull Term type
) implements ExprProblem {
  @Override
  public @NotNull Severity level() {
    return Severity.ERROR;
  }

  @Override
  public @NotNull Doc describe() {
    return Doc.vcat(
      Doc.english("Cannot check"),
      Doc.indent(1, expr.toDoc(DistillerOptions.DEFAULT)),
      Doc.english("against the Pi type"),
      Doc.indent(1, type.toDoc(DistillerOptions.DEFAULT)),
      Doc.english("because explicitnesses do not match")
    );
  }
}
