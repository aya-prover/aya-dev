// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.producer.error;

import org.aya.generic.Modifier;
import org.aya.prettier.BasePrettier;
import org.aya.pretty.doc.Doc;
import org.aya.util.PrettierOptions;
import org.aya.util.position.SourcePos;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;

public interface BadXWarn extends Problem {
  record BadPragmaWarn(@Override @NotNull SourcePos sourcePos, @NotNull String pragma) implements BadXWarn {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(Doc.english("Unrecognized pragma"), Doc.code(pragma),
        Doc.english("will be ignored."));
    }
  }

  record BadWarnWarn(@Override @NotNull SourcePos sourcePos, @NotNull String pragma) implements BadXWarn {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(Doc.english("Unrecognized warning"), Doc.code(pragma),
        Doc.english("will be ignored."));
    }
  }

  record BadModifierWarn(@Override @NotNull SourcePos sourcePos, @NotNull Modifier modifier) implements BadXWarn {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(Doc.plain("Ignoring"), Doc.styled(BasePrettier.KEYWORD, modifier.keyword));
    }
  }

  @Override default @NotNull Severity level() { return Severity.WARN; }
}
