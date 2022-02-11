// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.error;

import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

public record DuplicateExportError(
  @NotNull String name,
  @Override @NotNull SourcePos sourcePos
) implements ResolveProblem {
  @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
    return Doc.sep(
      Doc.english("The name"),
      Doc.styled(Style.code(), Doc.plain(name)),
      Doc.english("being exported clashes with another exported definition with the same name"));
  }

  @Override @NotNull public Severity level() {
    return Severity.ERROR;
  }
}
