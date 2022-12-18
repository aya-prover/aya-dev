// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import org.aya.concrete.Expr;
import org.aya.concrete.stmt.TeleDecl;
import org.aya.core.term.Term;
import org.aya.generic.ExprProblem;
import org.aya.pretty.doc.Doc;
import org.aya.util.error.SourcePos;
import org.aya.util.prettier.PrettierOptions;
import org.jetbrains.annotations.NotNull;

public sealed interface UnifyError extends TyckError {
  record Type(
    @Override @NotNull Expr expr,
    @NotNull Term expected,
    @NotNull Term actual,
    @NotNull UnifyInfo info
  ) implements ExprProblem, UnifyError {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      var prologue = Doc.vcat(
        Doc.english("Cannot check the expression"),
        Doc.par(1, expr.toDoc(options)),
        Doc.english("of type"));
      return info.describeUnify(options, prologue, actual, Doc.english("against the type"), expected);
    }
  }

  record ConReturn(
    @NotNull TeleDecl.DataCtor ctor,
    @NotNull Term expected,
    @NotNull Term actual,
    @NotNull UnifyInfo info
  ) implements UnifyError {
    @Override public @NotNull SourcePos sourcePos() {
      return ctor.sourcePos;
    }

    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      var prologue = Doc.vcat(
        Doc.english("Cannot make sense of the return type of the constructor"),
        Doc.par(1, ctor.toDoc(options)),
        Doc.english("which eventually returns"));
      return info.describeUnify(options, prologue, actual, Doc.english("while it should return"), expected);
    }
  }
}
