// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.error;

import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;

public record UnknownPrimError(
  @Override @NotNull SourcePos sourcePos,
  @NotNull String name
) implements Problem {
  @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
    return Doc.sep(
      Doc.plain("Unknown primitive"),
      Doc.styled(Style.code(), Doc.plain(name)));
  }

  @Override public @NotNull Stage stage() {
    return Stage.PARSE;
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }
}
