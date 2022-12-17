// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import org.aya.concrete.Expr;
import org.aya.core.term.Term;
import org.aya.generic.ExprProblem;
import org.aya.pretty.doc.Doc;
import org.aya.util.prettier.PrettierOptions;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;

public sealed interface LicitError extends Problem {
  @Override default @NotNull Severity level() {return Severity.ERROR;}
  @Override default @NotNull Stage stage() {return Stage.TYCK;}

  record LicitMismatch(@Override @NotNull Expr expr, @NotNull Term type) implements LicitError, ExprProblem {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.vcat(
        Doc.english("Cannot check"),
        Doc.par(1, expr.toDoc(options)),
        Doc.english("against the Pi type"),
        Doc.par(1, type.toDoc(options)),
        Doc.english("because explicitness do not match"));
    }
  }

  record UnexpectedImplicitArg(@Override @NotNull Expr.NamedArg expr) implements LicitError {
    @Override public @NotNull SourcePos sourcePos() {
      return expr.term().sourcePos();
    }

    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(Doc.english("Unexpected implicit argument"),
        Doc.code(expr.toDoc(options)));
    }
  }
}
