// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.parse;

import org.aya.prettier.BasePrettier;
import org.aya.pretty.doc.Doc;
import org.aya.util.error.SourcePos;
import org.aya.util.prettier.PrettierOptions;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;

public record ModifierError(
  @NotNull SourcePos sourcePos,
  @NotNull ModifierParser.Modifier modifier,
  @NotNull Reason reason
) implements Problem {
  enum Reason {
    Inappropriate,
    Contradictory,
    Duplicative
  }

  @Override
  public @NotNull Doc describe(@NotNull PrettierOptions options) {
    return Doc.sep(
      Doc.english("The modifier"),
      Doc.styled(BasePrettier.KEYWORD, modifier.keyword),
      Doc.english(switch (reason) {
        case Inappropriate -> "is not suitable here.";
        case Contradictory -> "contradicts the others.";
        case Duplicative -> "is redundant, ignored.";
      }));
  }

  @Override
  public @NotNull Severity level() {
    return Severity.ERROR;
  }
}
