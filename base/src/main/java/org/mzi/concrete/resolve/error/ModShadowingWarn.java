// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.resolve.error;

import org.glavo.kala.collection.Seq;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.error.Problem;
import org.mzi.api.error.SourcePos;
import org.mzi.pretty.doc.Doc;

public record ModShadowingWarn(
  @NotNull Seq<String> modName,
  @NotNull SourcePos sourcePos
) implements Problem.Warn {
  @Override
  public @NotNull Doc describe() {
    return Doc.hcat(
      Doc.plain("The newly created module name `"),
      Doc.plain(modName.joinToString("::")),
      Doc.plain("` shadows a previous definition from outer scope")
    );
  }

  @Override public @NotNull Stage stage() {
    return Stage.RESOLVE;
  }
}
