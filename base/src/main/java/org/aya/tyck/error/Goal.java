// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.error;

import org.aya.api.distill.DistillerOptions;
import org.aya.api.error.ExprProblem;
import org.aya.api.util.NormalizeMode;
import org.aya.concrete.Expr;
import org.aya.core.Meta;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

public record Goal(
  @NotNull Expr.HoleExpr expr,
  @NotNull Meta meta
) implements ExprProblem {
  @Override public @NotNull Doc describe() {
    var doc = Doc.vcat(
      Doc.english("Goal of type"),
      Doc.par(1, meta.result.toDoc(DistillerOptions.DEFAULT)),
      Doc.par(1, Doc.parened(Doc.sep(Doc.plain("Normalized:"), meta.result.normalize(NormalizeMode.NF).toDoc(DistillerOptions.DEFAULT)))),
      Doc.plain("Context:"),
      Doc.vcat(meta.fullTelescope().map(param -> param.toDoc(DistillerOptions.DEFAULT)))
    );
    return meta.body == null ? doc :
      Doc.vcat(Doc.plain("Candidate exists:"), Doc.par(1, meta.body.toDoc(DistillerOptions.DEFAULT)), doc);
  }

  @Override public @NotNull Severity level() {
    return Severity.GOAL;
  }
}
