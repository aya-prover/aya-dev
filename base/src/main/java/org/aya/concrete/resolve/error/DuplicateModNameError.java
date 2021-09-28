// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.resolve.error;

import kala.collection.Seq;
import org.aya.api.error.SourcePos;
import org.aya.concrete.stmt.QualifiedID;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.jetbrains.annotations.NotNull;

public record DuplicateModNameError(
  @NotNull Seq<String> modName,
  @Override @NotNull SourcePos sourcePos
) implements ResolveProblem {
  @Override public @NotNull Doc describe() {
    return Doc.sep(
      Doc.english("The module name"),
      Doc.styled(Style.code(), Doc.plain(QualifiedID.join(modName))),
      Doc.english("is already defined elsewhere")
    );
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }
}
