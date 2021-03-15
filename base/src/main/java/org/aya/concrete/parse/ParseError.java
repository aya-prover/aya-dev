// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.parse;

import org.aya.api.error.Problem;
import org.aya.api.error.SourcePos;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

public record ParseError(@NotNull SourcePos sourcePos, @NotNull String message) implements Problem {
  @Override public @NotNull Doc describe() {
    return Doc.plain("Parser error: " + message);
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }
}
