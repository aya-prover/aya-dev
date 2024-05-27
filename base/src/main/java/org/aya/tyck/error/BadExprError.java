// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import org.aya.generic.AyaDocile;
import org.aya.pretty.doc.Doc;
import org.aya.syntax.core.term.Term;
import org.aya.util.error.SourcePos;
import org.aya.util.prettier.PrettierOptions;
import org.jetbrains.annotations.NotNull;

public record BadExprError(
  @Override @NotNull AyaDocile expr,
  @NotNull SourcePos sourcePos,
  @NotNull Term expectedTy
) implements TyckError {
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
