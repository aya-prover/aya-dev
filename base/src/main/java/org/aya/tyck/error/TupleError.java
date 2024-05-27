// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import org.aya.pretty.doc.Doc;
import org.aya.syntax.concrete.Expr;
import org.aya.util.error.SourcePos;
import org.aya.util.prettier.PrettierOptions;
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

  record ProjIxError(
    @Override @NotNull Expr.Proj expr,
    @Override @NotNull SourcePos sourcePos,
    int actual, int expectedBound
  ) implements TupleError {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(
        Doc.english("Cannot project the"),
        Doc.ordinal(actual),
        Doc.english("element because the type has index range"),
        Doc.plain(STR."[1, \{expectedBound}]")
      );
    }
  }
}
