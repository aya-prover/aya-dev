// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.resolve.error;

import org.aya.api.error.SourcePos;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.jetbrains.annotations.NotNull;

public record UnknownOperatorError(
  @Override @NotNull SourcePos sourcePos,
  @NotNull String name
) implements ResolveProblem {
  @Override public @NotNull Doc describe() {
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
