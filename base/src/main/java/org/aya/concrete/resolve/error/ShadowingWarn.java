// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.resolve.error;

import org.aya.api.distill.DistillerOptions;
import org.aya.api.error.SourcePos;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.jetbrains.annotations.NotNull;

public record ShadowingWarn(
  @NotNull String name,
  @Override @NotNull SourcePos sourcePos
) implements ResolveProblem {
  @Override public @NotNull Severity level() {
    return Severity.WARN;
  }

  @Override public @NotNull Doc describe(DistillerOptions options) {
    return Doc.sep(Doc.english("The name"),
      Doc.styled(Style.code(), Doc.plain(name)),
      Doc.english("shadows a previous local definition from outer scope")
    );
  }
}
