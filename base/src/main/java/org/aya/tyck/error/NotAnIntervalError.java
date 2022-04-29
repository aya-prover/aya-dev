// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;

public record NotAnIntervalError(
  @NotNull SourcePos sourcePos,
  int integer
) implements Problem {
  @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
    return Doc.sep(Doc.english("The point"),
      Doc.styled(Style.code(), String.valueOf(integer)),
      Doc.english("does not live in interval"));
  }

  @Override public @NotNull Doc hint(@NotNull DistillerOptions options) {
    return Doc.sep(Doc.english("Did you mean: "),
      Doc.styled(Style.code(), "0"),
      Doc.plain("or"),
      Doc.styled(Style.code(), "1")
    );
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }
}
