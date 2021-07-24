// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.error;

import kala.tuple.Unit;
import org.aya.api.error.ExprProblem;
import org.aya.api.util.NormalizeMode;
import org.aya.concrete.Expr;
import org.aya.core.term.Term;
import org.aya.core.visitor.Zonker;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

public record BadTypeError(
  @NotNull Expr expr,
  @NotNull Doc expectedType,
  @NotNull Term actualType
) implements ExprProblem {
  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }

  @Override public @NotNull Doc describe() {
    return Doc.vcat(
      Doc.cat(Doc.plain("Expected type: "), actualType.accept(Zonker.NO_REPORT, Unit.unit()).toDoc()),
      Doc.cat(Doc.plain("Normalized: "), actualType.normalize(NormalizeMode.NF).toDoc()),
      Doc.cat(Doc.plain("Want: "), expectedType),
      Doc.cat(Doc.english("Because we want to type a term such as:"), Doc.ONE_WS, expr.toDoc())
    );
  }
}
