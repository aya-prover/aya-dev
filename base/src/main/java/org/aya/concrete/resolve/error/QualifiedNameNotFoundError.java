// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.resolve.error;

import kala.collection.Seq;
import org.aya.api.distill.DistillerOptions;
import org.aya.api.error.SourcePos;
import org.aya.concrete.stmt.QualifiedID;
import org.aya.generic.Constants;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.jetbrains.annotations.NotNull;

public record QualifiedNameNotFoundError(
  @NotNull Seq<String> modName,
  @NotNull String name,
  @Override @NotNull SourcePos sourcePos
) implements ResolveProblem {
  @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
    return Doc.sep(
      Doc.english("The qualified name"),
      Doc.styled(Style.code(),
        Doc.cat(Doc.plain(QualifiedID.join(modName)), Doc.plain(Constants.SCOPE_SEPARATOR), Doc.plain(name))),
      Doc.english("is not defined in the current scope")
    );
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }
}
