// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import org.aya.generic.AyaDocile;
import org.aya.pretty.doc.Doc;
import org.aya.syntax.core.term.Term;
import org.aya.util.PrettierOptions;
import org.aya.util.position.SourcePos;
import org.jetbrains.annotations.NotNull;

public interface DoubleCheckError {
  record RuleError(
    @Override @NotNull AyaDocile expr,
    @NotNull SourcePos sourcePos,
    @NotNull Term expectedTy
  ) implements TyckError {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.vcat(
        Doc.english("The term given to the double checker is"),
        Doc.par(1, expr.toDoc(options)),
        Doc.english("but the expected type"),
        Doc.par(1, expectedTy.toDoc(options)),
        Doc.english("does not like this term.")
      );
    }
  }

  record BoundaryError(
    @NotNull SourcePos sourcePos,
    @NotNull UnifyInfo info,
    @NotNull UnifyInfo.Comparison comparison
  ) implements TyckError {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return info.describeUnify(options, comparison,
        Doc.english("When double-checking the boundary, there is a mismatch between:"),
        Doc.plain("and"));
    }
  }
}
