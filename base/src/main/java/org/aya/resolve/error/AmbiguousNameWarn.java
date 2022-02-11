// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.error;

import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

public record AmbiguousNameWarn(
  @NotNull String name,
  @Override @NotNull SourcePos sourcePos
) implements ResolveProblem {
  @Override public @NotNull Severity level() {
    return Severity.WARN;
  }

  @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
    return Doc.vcat(Doc.sep(
      Doc.english("The name"),
      Doc.styled(Style.code(), Doc.plain(name)),
      Doc.english("introduces ambiguity and can only be accessed through a qualified name")));
  }
}
