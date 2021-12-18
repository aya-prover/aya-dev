// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.error;

import org.aya.api.distill.DistillerOptions;
import org.aya.api.error.Problem;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

public record RedefinitionPrimError(
  @NotNull String name,
  @Override @NotNull SourcePos sourcePos
) implements Problem {
  @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
    return Doc.sep(Doc.english("Redefinition of primitive"),
      Doc.styled(Style.code(), Doc.plain(name)));
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }

  @Override public @NotNull Stage stage() {
    return Stage.PARSE;
  }
}
