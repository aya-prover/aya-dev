// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import org.aya.pretty.doc.Doc;
import org.aya.syntax.concrete.Expr;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.aya.util.prettier.PrettierOptions;
import org.jetbrains.annotations.NotNull;

public interface ClassError extends TyckError, SourceNodeProblem {
  @Override default @NotNull SourcePos sourcePos() { return expr().sourcePos(); }

  record NotFullyApplied(@Override @NotNull WithPos<Expr> expr) implements ClassError {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(Doc.english("Unable to new an incomplete class type:"), Doc.code(expr.data().toDoc(options)));
    }
  }

  record UnknownMember(
    @Override @NotNull SourcePos sourcePos,
    @NotNull String name
  ) implements TyckError {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(
        Doc.english("Unknown member"),
        Doc.code(name),
        Doc.english("projected")
      );
    }
  }
}
