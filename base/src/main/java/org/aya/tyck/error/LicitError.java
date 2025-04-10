// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import org.aya.pretty.doc.Doc;
import org.aya.syntax.concrete.Expr;
import org.aya.util.PrettierOptions;
import org.jetbrains.annotations.NotNull;

public sealed interface LicitError extends TyckError, SourceNodeProblem {
  record BadImplicitArg(@Override @NotNull Expr.NamedArg expr) implements LicitError {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(Doc.english("Unexpected implicit argument"),
        Doc.code(expr.toDoc(options)));
    }
  }

  record BadNamedArg(@Override @NotNull Expr.NamedArg expr) implements LicitError {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.vcat(
        Doc.sep(Doc.english("Named argument unwanted here:"),
          Doc.code(expr.toDoc(options))),
        Doc.english("Named applications are only allowed in direct application to definitions."));
    }
  }
}
