// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.error;

import kala.collection.Seq;
import org.aya.api.distill.DistillerOptions;
import org.aya.concrete.stmt.QualifiedID;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

public record ModNotFoundError(
  @NotNull Seq<String> modName,
  @Override @NotNull SourcePos sourcePos
) implements ResolveProblem {
  @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
    return Doc.sep(
      Doc.english("The module name"),
      Doc.styled(Style.code(), Doc.plain(QualifiedID.join(modName))),
      Doc.english("is not found")
    );
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }
}
