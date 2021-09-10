// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.resolve.error;

import org.aya.api.error.Problem;
import org.aya.api.error.SourcePos;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.jetbrains.annotations.NotNull;

public record DuplicateExportError(
  @NotNull String name,
  @Override @NotNull SourcePos sourcePos
) implements Problem {
  @Override public @NotNull Doc describe() {
    return Doc.sep(
      Doc.english("The name"),
      Doc.styled(Style.code(), Doc.plain(name)),
      Doc.english("being exported clashes with another exported definition with the same name"));
  }

  @Override public @NotNull Stage stage() {
    return Stage.RESOLVE;
  }

  @Override @NotNull public Severity level() {
    return Severity.ERROR;
  }
}
