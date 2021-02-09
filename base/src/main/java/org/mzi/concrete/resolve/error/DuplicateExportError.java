// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.resolve.error;

import org.jetbrains.annotations.NotNull;
import org.mzi.api.error.Problem;
import org.mzi.api.error.SourcePos;
import org.mzi.pretty.doc.Doc;

public record DuplicateExportError(
  @NotNull String name,
  @NotNull SourcePos sourcePos
) implements Problem.Error {
  @Override
  public @NotNull Doc describe() {
    return Doc.hcat(
      Doc.plain("The name being exported `"),
      Doc.plain(name),
      Doc.plain("` clashes with another exported definition with the same name.")
    );
  }

  @Override public @NotNull Stage stage() {
    return Stage.RESOLVE;
  }
}
