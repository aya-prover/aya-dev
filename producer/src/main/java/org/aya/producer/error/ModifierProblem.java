// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.producer.error;

import org.aya.prettier.BasePrettier;
import org.aya.pretty.doc.Doc;
import org.aya.producer.ModifierParser;
import org.aya.util.error.SourcePos;
import org.aya.util.prettier.PrettierOptions;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;

public record ModifierProblem(
  @NotNull SourcePos sourcePos,
  @NotNull ModifierParser.CModifier modifier,
  @NotNull Reason reason
) implements Problem {
  public enum Reason {
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

  @Override public @NotNull Severity level() {
    return reason == Reason.Duplicative ? Severity.WARN : Severity.ERROR;
  }
}
