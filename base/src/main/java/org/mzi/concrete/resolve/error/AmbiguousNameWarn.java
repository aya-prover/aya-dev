// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.resolve.error;

import org.jetbrains.annotations.NotNull;
import org.mzi.api.error.Problem;
import org.mzi.api.error.SourcePos;
import org.mzi.pretty.doc.Doc;

public record AmbiguousNameWarn(
  @NotNull String name,
  @NotNull SourcePos sourcePos
) implements Problem.Warn {
  @Override
  public @NotNull Doc describe() {
    return Doc.hcat(
      Doc.plain("The name being defined `"),
      Doc.plain(name),
      Doc.plain("` introduces ambiguity. "),
      Doc.plain("It can only be accessed through a qualified name.")
    );
  }
}
