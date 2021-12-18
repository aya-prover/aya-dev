// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.error;

import org.aya.api.distill.DistillerOptions;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

public record UnknownOperatorError(
  @Override @NotNull SourcePos sourcePos,
  @NotNull String name
) implements ResolveProblem {
  @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
    return Doc.sep(
      Doc.english("Unknown operator"),
      Doc.styled(Style.code(), Doc.plain(name)),
      Doc.english("used in bind statement")
    );
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }
}
