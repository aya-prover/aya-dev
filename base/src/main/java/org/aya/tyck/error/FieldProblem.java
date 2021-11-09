// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.distill.DistillerOptions;
import org.aya.api.error.ExprProblem;
import org.aya.api.error.Problem;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.Var;
import org.aya.concrete.Expr;
import org.aya.distill.BaseDistiller;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.jetbrains.annotations.NotNull;

public sealed interface FieldProblem extends Problem {
  record MissingFieldError(
    @Override @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<Var> missing
  ) implements FieldProblem {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.sep(Doc.english("Missing field(s):"), Doc.commaList(missing.view()
        .map(BaseDistiller::varDoc)
        .map(m -> Doc.styled(Style.code(), m))));
    }
  }

  @Override default @NotNull Severity level() {
    return Severity.ERROR;
  }
  record NoSuchFieldError(
    @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<String> notFound
  ) implements FieldProblem {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.sep(Doc.english("No such field(s):"),
        Doc.commaList(notFound.view()
          .map(m -> Doc.styled(Style.code(), Doc.plain(m))))
      );
    }
  }

  record UnknownField(
    @Override @NotNull Expr.ProjExpr expr,
    @NotNull String name
  ) implements FieldProblem, ExprProblem {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.sep(
        Doc.english("Unknown field"),
        Doc.styled(Style.code(), Doc.plain(name)),
        Doc.english("projected")
      );
    }
  }
}
