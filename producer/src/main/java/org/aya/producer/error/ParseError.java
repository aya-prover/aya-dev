// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.producer.error;

import org.aya.pretty.doc.Doc;
import org.aya.util.error.SourcePos;
import org.aya.util.prettier.PrettierOptions;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;

public record ParseError(@Override @NotNull SourcePos sourcePos, @NotNull String message) implements Problem {
  @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
    return Doc.english(message);
  }

  @Override public @NotNull Severity level() { return Severity.ERROR; }
}
