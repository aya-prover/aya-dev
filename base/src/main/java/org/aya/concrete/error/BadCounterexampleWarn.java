// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.error;

import org.aya.api.distill.DistillerOptions;
import org.aya.api.error.Problem;
import org.aya.concrete.stmt.Stmt;
import org.aya.pretty.doc.Doc;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

public record BadCounterexampleWarn(@NotNull Stmt stmt) implements Problem {
  @Override public @NotNull SourcePos sourcePos() {
    return stmt.sourcePos();
  }

  @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
    return Doc.vcat(Doc.english("Ignoring the following statement, which is not a counterexample:"),
      stmt.toDoc(options));
  }

  @Override public @NotNull Severity level() {
    return Severity.WARN;
  }
}
