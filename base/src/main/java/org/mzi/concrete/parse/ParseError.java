// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.parse;

import org.jetbrains.annotations.NotNull;
import org.mzi.api.error.Problem;
import org.mzi.api.error.SourcePos;
import org.mzi.pretty.doc.Doc;

public record ParseError(@NotNull SourcePos sourcePos, @NotNull String message) implements Problem.Error {
  @Override public @NotNull Doc describe() {
    return Doc.plain("Parser error: " + message);
  }
}
