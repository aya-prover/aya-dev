// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.error;

import org.aya.concrete.stmt.Stmt;
import org.aya.pretty.doc.Doc;
import org.aya.util.prettier.PrettierOptions;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;

public record BadCounterexampleWarn(@NotNull Stmt stmt) implements Problem {
  @Override public @NotNull SourcePos sourcePos() {
    return stmt.sourcePos();
  }

  @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
    return Doc.vcat(Doc.english("Ignoring the following statement, which is not a counterexample:"),
      stmt.toDoc(options));
  }

  @Override public @NotNull Severity level() {
    return Severity.WARN;
  }
}
