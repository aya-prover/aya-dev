// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.literate;

import org.aya.pretty.doc.Doc;
import org.aya.util.PrettierOptions;
import org.aya.util.position.SourcePos;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;

public interface LabelProblems extends Problem {
  @NotNull String label();

  @Override
  default @NotNull Severity level() {
    return Severity.WARN;
  }

  record Redefinition(
    @Override @NotNull SourcePos sourcePos,
    @Override @NotNull String label
  ) implements LabelProblems {
    @Override
    public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(
        Doc.english("Redefinition of label"),
        Doc.plain(label)
      );
    }
  }

  record UnknownLabel(
    @Override @NotNull SourcePos sourcePos,
    @Override @NotNull String label
  ) implements LabelProblems {
    @Override
    public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(
        Doc.english("Label"),
        Doc.plain(label),
        Doc.english("is undefined."));
    }
  }
}
