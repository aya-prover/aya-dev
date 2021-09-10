// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.error;

import org.aya.api.distill.DistillerOptions;
import org.aya.api.error.ExprProblem;
import org.aya.api.util.NormalizeMode;
import org.aya.concrete.Expr;
import org.aya.core.term.Term;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.jetbrains.annotations.NotNull;

public interface BadTypeError extends ExprProblem {
  @Override default @NotNull Severity level() {
    return Severity.ERROR;
  }

  private static @NotNull Doc genDescribe(@NotNull Expr expr, @NotNull Term actualType, @NotNull Doc action, @NotNull Doc thing, @NotNull Doc desired) {
    return Doc.vcat(
      Doc.sep(Doc.english("Unable to"), action, Doc.english("the expression")),
      Doc.par(1, expr.toDoc(DistillerOptions.DEFAULT)),
      Doc.sep(Doc.english("because the type"), thing, Doc.english("is not a"), Doc.cat(desired, Doc.plain(",")), Doc.english("but instead:")),
      Doc.par(1, actualType.toDoc(DistillerOptions.DEFAULT)),
      Doc.par(1, Doc.parened(Doc.sep(Doc.plain("Normalized:"), actualType.normalize(NormalizeMode.NF).toDoc(DistillerOptions.DEFAULT))))
    );
  }

  record Pi(
    @NotNull Expr expr,
    @NotNull Term actualType
  ) implements BadTypeError {
    @Override public @NotNull Doc describe() {
      return genDescribe(expr, actualType, Doc.plain("apply"),
        Doc.english("of what you applied"), Doc.english("Pi type"));
    }
  }

  record SigmaAcc(
    @NotNull Expr expr,
    int ix,
    @NotNull Term actualType
  ) implements BadTypeError {
    @Override public @NotNull Doc describe() {
      return genDescribe(expr, actualType,
        Doc.sep(Doc.english("project the"), Doc.ordinal(ix), Doc.english("element of")),
        Doc.english("of what you projected on"),
        Doc.english("Sigma type"));
    }
  }

  record SigmaCon(
    @NotNull Expr expr,
    @NotNull Term actualType
  ) implements BadTypeError {
    @Override public @NotNull Doc describe() {
      return genDescribe(expr, actualType,
        Doc.sep(Doc.plain("construct")),
        Doc.english("you checks it against"),
        Doc.english("Sigma type"));
    }
  }

  record StructAcc(
    @NotNull Expr expr,
    @NotNull String fieldName,
    @NotNull Term actualType
  ) implements BadTypeError {
    @Override public @NotNull Doc describe() {
      return genDescribe(expr, actualType,
        Doc.sep(Doc.english("access field"), Doc.styled(Style.code(), Doc.plain(fieldName)), Doc.plain("of")),
        Doc.english("of what you accessed"),
        Doc.english("struct type"));
    }
  }

  record StructCon(
    @NotNull Expr expr,
    @NotNull Term actualType
  ) implements BadTypeError {
    @Override public @NotNull Doc describe() {
      return genDescribe(expr, actualType,
        Doc.sep(Doc.plain("construct")),
        Doc.english("you gave"),
        Doc.english("struct type"));
    }
  }
}
