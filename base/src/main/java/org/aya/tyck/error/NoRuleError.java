// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import org.aya.pretty.doc.Doc;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.core.term.Term;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.aya.util.prettier.PrettierOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record NoRuleError(@NotNull WithPos<@NotNull Expr> expr, @Nullable Term term) implements TyckError {
  @Override public @NotNull SourcePos sourcePos() { return expr.sourcePos(); }

  @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
    if (term != null) return Doc.sep(
      Doc.english("No rule for checking the expression"),
      expr.data().toDoc(options),
      Doc.english("against the type"),
      term.toDoc(options));
    return Doc.sep(Doc.english("No rule inferring the type of"), expr.data().toDoc(options));
  }
}
