// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import org.aya.concrete.Expr;
import org.aya.generic.ExprProblem;
import org.aya.pretty.doc.Doc;
import org.aya.util.pretty.PrettierOptions;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

public sealed interface TupleError extends TyckError {
  record ElemMismatchError(@NotNull SourcePos sourcePos, int expected, int supplied) implements TupleError {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(Doc.english("Expected"),
        Doc.plain(String.valueOf(expected)),
        Doc.english("elements in the tuple, but found"),
        Doc.plain(String.valueOf(supplied)));
    }
  }

  record ProjIxError(@Override @NotNull Expr.Proj expr, int actual, int expectedBound)
    implements ExprProblem, TupleError {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(
        Doc.english("Cannot project the"),
        Doc.ordinal(actual),
        Doc.english("element because the type has index range"),
        Doc.plain("[1, " + expectedBound + "]")
      );
    }
  }
}
