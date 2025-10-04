// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.issue.error;

import org.aya.pretty.doc.Doc;
import org.aya.util.PrettierOptions;
import org.aya.util.position.SourcePos;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface BlockParserProblem extends Problem {
  record UnexpectedMarker(
    @Override @NotNull SourcePos sourcePos,
    boolean isBegin,
    @Nullable String expected,
    @NotNull String actual
  ) implements BlockParserProblem {

    @Override
    public @NotNull Doc describe(@NotNull PrettierOptions options) {
      if (expected != null) {
        return Doc.english(
          "Expecting end marker of " + expected + ", but got " + (isBegin ? "begin" : "end") + " marker of " + actual + "."
        );
      } else {
        assert !isBegin;
        return Doc.english("Unexpected end marker: " + actual + ".");
      }
    }

    @Override
    public @NotNull Severity level() {
      return Severity.WARN;
    }
  }

  record UnclosedBlock(
    @Override @NotNull SourcePos sourcePos,
    @NotNull String expected
  ) implements BlockParserProblem {
    @Override
    public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.english("Expecting end marker of " + expected + ", but got EOF.");
    }

    @Override
    public @NotNull Severity level() {
      return Severity.WARN;
    }
  }
}
