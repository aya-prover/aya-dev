// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import org.aya.concrete.Expr;
import org.aya.core.term.Term;
import org.aya.generic.AyaDocile;
import org.aya.pretty.doc.Doc;
import org.aya.util.error.SourcePos;
import org.aya.util.prettier.PrettierOptions;
import org.jetbrains.annotations.NotNull;

public record BadExprError(
  @Override @NotNull AyaDocile expr,
  @NotNull SourcePos sourcePos,
  @NotNull Term expectedTy
) implements TyckError {
  public BadExprError(@NotNull Expr expr, @NotNull Term expectedTy) {
    this(expr, expr.sourcePos(), expectedTy);
  }

  @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
    return Doc.vcat(
      Doc.english("The expected type is"),
      Doc.par(1, expectedTy.toDoc(options)),
      Doc.english("but the given expression"),
      Doc.par(1, expr.toDoc(options)),
      Doc.english("does not try to construct something into this type")
    );
  }
}
