// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.resolve.error;

import org.aya.api.error.Problem;
import org.aya.api.error.SourcePos;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.jetbrains.annotations.NotNull;

public record UnknownPrimError(
  @Override @NotNull SourcePos sourcePos,
  @NotNull String name
) implements Problem {
  @Override public @NotNull Doc describe() {
    return Doc.sep(
      Doc.plain("Unknown primitive"),
      Doc.styled(Style.code(), Doc.plain(name)));
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }
}
