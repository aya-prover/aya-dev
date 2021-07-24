// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.resolve.error;

import org.aya.api.error.Problem;
import org.aya.api.error.SourcePos;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.jetbrains.annotations.NotNull;

public record UnknownOperatorError(@NotNull SourcePos sourcePos, @NotNull String name) implements Problem {
  @Override public @NotNull Doc describe() {
    return Doc.hsep(
      Doc.english("Unknown operator"),
      Doc.styled(Style.code(), Doc.plain(name)),
      Doc.english("used in bind statement")
    );
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }
}
