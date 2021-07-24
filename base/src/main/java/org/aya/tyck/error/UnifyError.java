// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.error;

import kala.tuple.Unit;
import org.aya.api.error.ExprProblem;
import org.aya.api.error.Problem;
import org.aya.api.util.NormalizeMode;
import org.aya.concrete.Expr;
import org.aya.core.term.Term;
import org.aya.core.visitor.Zonker;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

public record UnifyError(
  @NotNull Expr expr,
  @NotNull Term expected,
  @NotNull Term actual
) implements ExprProblem, Problem {
  @Override public @NotNull Doc describe() {
    return Doc.vcat(
      Doc.cat(Doc.plain("Expected type: "), expected.accept(Zonker.NO_REPORT, Unit.unit()).toDoc()),
      Doc.cat(Doc.plain("Normalized: "), expected.normalize(NormalizeMode.NF).toDoc()),
      Doc.cat(Doc.plain("Actual type: "), actual.accept(Zonker.NO_REPORT, Unit.unit()).toDoc()),
      Doc.cat(Doc.plain("Normalized: "), actual.normalize(NormalizeMode.NF).toDoc()),
      Doc.english("They don't match, sorry")
    );
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }
}
