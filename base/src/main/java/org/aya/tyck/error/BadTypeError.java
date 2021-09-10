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
      Doc.sep(Doc.plain("Unable to"), action, Doc.plain("the expression")),
      Doc.par(1, expr.toDoc(DistillerOptions.DEFAULT)),
      Doc.sep(Doc.plain("because the type"), thing, Doc.plain("is not a"), Doc.cat(desired, Doc.plain(",")), Doc.plain("but instead:")),
      Doc.par(1, actualType.toDoc(DistillerOptions.DEFAULT)),
      Doc.par(1, Doc.parened(Doc.sep(Doc.plain("Normalized:"), actualType.normalize(NormalizeMode.NF).toDoc(DistillerOptions.DEFAULT))))
    );
  }

  record Pi(
    @NotNull Expr expr,
    @NotNull Term actualType
  ) implements BadTypeError {
    @Override public @NotNull Doc describe() {
      return genDescribe(expr, actualType, Doc.plain("apply"), Doc.plain("of what you applied"), Doc.plain("Pi type"));
    }
  }

  record SigmaAcc(
    @NotNull Expr expr,
    int ix,
    @NotNull Term actualType
  ) implements BadTypeError {
    @Override public @NotNull Doc describe() {
      return genDescribe(expr, actualType,
        Doc.sep(Doc.plain("project the"), Doc.ordinal(ix), Doc.plain("element of")),
        Doc.plain("of what you projected on"),
        Doc.plain("Sigma type"));
    }
  }

  record SigmaCon(
    @NotNull Expr expr,
    @NotNull Term actualType
  ) implements BadTypeError {
    @Override public @NotNull Doc describe() {
      return genDescribe(expr, actualType,
        Doc.sep(Doc.plain("construct")),
        Doc.plain("you checks it against"),
        Doc.plain("Sigma type"));
    }
  }

  record StructAcc(
    @NotNull Expr expr,
    @NotNull String fieldName,
    @NotNull Term actualType
  ) implements BadTypeError {
    @Override public @NotNull Doc describe() {
      return genDescribe(expr, actualType,
        Doc.sep(Doc.plain("access field"), Doc.styled(Style.code(), Doc.plain(fieldName)), Doc.plain("of")),
        Doc.plain("of what you accessed"),
        Doc.plain("struct type"));
    }
  }

  record StructCon(
    @NotNull Expr expr,
    @NotNull Term actualType
  ) implements BadTypeError {
    @Override public @NotNull Doc describe() {
      return genDescribe(expr, actualType,
        Doc.sep(Doc.plain("construct")),
        Doc.plain("you gave"),
        Doc.plain("struct type"));
    }
  }
}
