// Copyright (c) 2020-2026 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.terck;

import org.aya.pretty.doc.Doc;
import org.aya.syntax.ref.DefVar;
import org.aya.util.PrettierOptions;
import org.aya.util.position.SourcePos;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;

public record BadInduction(
  @NotNull DefVar<?, ?> con
) implements Problem {
  @Override
  public @NotNull SourcePos sourcePos() {
    return con.concrete.nameSourcePos();
  }

  @Override
  public @NotNull Doc describe(@NotNull PrettierOptions options) {
    return Doc.english("This constructor doesn't satisfy strictly positivity.");
  }

  @Override
  public @NotNull Severity level() {
    return Severity.ERROR;
  }
}
