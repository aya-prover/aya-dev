// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.parse.error;

import org.aya.cli.parse.AyaGKProducer;
import org.aya.cli.parse.ModifierParser;
import org.aya.prettier.BasePrettier;
import org.aya.pretty.doc.Doc;
import org.aya.util.error.SourcePos;
import org.aya.util.prettier.PrettierOptions;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;

public record NotSuitableModifierWarn(
  @NotNull SourcePos sourcePos,
  @NotNull ModifierParser.Modifier modifier
) implements Problem {
  @Override
  public @NotNull Doc describe(@NotNull PrettierOptions options) {
    return Doc.sep(
      Doc.english("The modifier"),
      Doc.styled(BasePrettier.KEYWORD, modifier.keyword),
      Doc.english("is not suitable here."));
  }

  @Override
  public @NotNull Severity level() {
    return Severity.WARN;
  }
}
