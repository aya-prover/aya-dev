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

public record BadTypeError(
  @Override @NotNull Expr expr,
  @NotNull Term actualType, @NotNull Doc action,
  @NotNull Doc thing, @NotNull Doc desired
) implements ExprProblem {
  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }

  @Override public @NotNull Doc describe() {
    return Doc.vcat(
      Doc.sep(Doc.english("Unable to"), action, Doc.english("the expression")),
      Doc.par(1, expr.toDoc(DistillerOptions.DEFAULT)),
      Doc.sep(Doc.english("because the type"), thing, Doc.english("is not a"), Doc.cat(desired, Doc.plain(",")), Doc.english("but instead:")),
      Doc.par(1, actualType.toDoc(DistillerOptions.DEFAULT)),
      Doc.par(1, Doc.parened(Doc.sep(Doc.plain("Normalized:"), actualType.normalize(NormalizeMode.NF).toDoc(DistillerOptions.DEFAULT))))
    );
  }

  public static @NotNull BadTypeError pi(@NotNull Expr expr, @NotNull Term actualType) {
    return new BadTypeError(expr, actualType, Doc.plain("apply"),
      Doc.english("of what you applied"), Doc.english("Pi type"));
  }

  public static @NotNull BadTypeError sigmaAcc(@NotNull Expr expr, int ix, @NotNull Term actualType) {
    return new BadTypeError(expr, actualType,
      Doc.sep(Doc.english("project the"), Doc.ordinal(ix), Doc.english("element of")),
      Doc.english("of what you projected on"),
      Doc.english("Sigma type"));
  }

  public static @NotNull BadTypeError sigmaCon(@NotNull Expr expr, @NotNull Term actualType) {
    return new BadTypeError(expr, actualType,
      Doc.sep(Doc.plain("construct")),
      Doc.english("you checks it against"),
      Doc.english("Sigma type"));
  }

  public static @NotNull BadTypeError structAcc(@NotNull Expr expr, @NotNull String fieldName, @NotNull Term actualType) {
    return new BadTypeError(expr, actualType,
      Doc.sep(Doc.english("access field"), Doc.styled(Style.code(), Doc.plain(fieldName)), Doc.plain("of")),
      Doc.english("of what you accessed"),
      Doc.english("struct type"));
  }

  public static @NotNull BadTypeError structCon(@NotNull Expr expr, @NotNull Term actualType) {
    return new BadTypeError(expr, actualType,
      Doc.sep(Doc.plain("construct")),
      Doc.english("you gave"),
      Doc.english("struct type"));
  }
}
