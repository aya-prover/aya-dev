// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.error;

import org.aya.prettier.BasePrettier;
import org.aya.pretty.doc.Doc;
import org.aya.syntax.ref.AnyVar;
import org.aya.util.error.SourcePos;
import org.aya.util.prettier.PrettierOptions;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;

public record GeneralizedNotAvailableError(
  @Override @NotNull SourcePos sourcePos, @NotNull AnyVar var
) implements Problem {
  @Override public @NotNull Severity level() { return Severity.ERROR; }
  @Override public @NotNull Stage stage() { return Stage.RESOLVE; }
  @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
    return Doc.sep(
      Doc.english("The generalized variable"),
      Doc.code(BasePrettier.varDoc(var)),
      Doc.english("is not available here")
    );
  }
}
