// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.Expr;
import org.aya.core.def.FieldDef;
import org.aya.distill.BaseDistiller;
import org.aya.generic.ExprProblem;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.aya.ref.AnyVar;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;

public sealed interface FieldProblem extends Problem {
  record MissingFieldError(
    @Override @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<AnyVar> missing
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

  record ArgMismatchError(
    @Override @NotNull SourcePos sourcePos,
    @NotNull FieldDef fieldDef,
    int supplied
  ) implements FieldProblem {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.sep(Doc.english("Expected"),
        Doc.plain(String.valueOf(fieldDef.ref.core.selfTele.size())),
        Doc.english("arguments, but found"),
        Doc.plain(String.valueOf(supplied)),
        Doc.english("arguments for field"),
        BaseDistiller.linkRef(fieldDef.ref, BaseDistiller.FIELD_CALL));
    }
  }
}
