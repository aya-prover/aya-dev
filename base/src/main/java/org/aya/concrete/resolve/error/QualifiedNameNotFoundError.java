// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.resolve.error;

import org.aya.api.error.Problem;
import org.aya.api.error.SourcePos;
import org.aya.pretty.doc.Doc;
import org.glavo.kala.collection.Seq;
import org.jetbrains.annotations.NotNull;

public record QualifiedNameNotFoundError(
  @NotNull Seq<String> modName,
  @NotNull String name,
  @NotNull SourcePos sourcePos
) implements Problem {
  @Override
  public @NotNull Doc describe() {
    return Doc.hcat(
      Doc.plain("The qualified name referred to by `"),
      Doc.plain(modName.joinToString("::")),
      Doc.plain(name),
      Doc.plain("` is not defined in the current scope")
    );
  }

  @Override public @NotNull Stage stage() {
    return Stage.RESOLVE;
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }
}
