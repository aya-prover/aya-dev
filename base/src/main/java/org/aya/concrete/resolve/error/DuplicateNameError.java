// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.resolve.error;

import org.aya.api.error.Problem;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.Var;
import org.aya.core.visitor.CoreDistiller;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.jetbrains.annotations.NotNull;

public record DuplicateNameError(
  @NotNull String name, @NotNull Var ref,
  @NotNull SourcePos sourcePos
) implements Problem {
  @Override public @NotNull Doc describe() {
    return Doc.sep(
      Doc.english("The name being added"),
      Doc.plain(name),
      Doc.parened(Doc.styled(Style.code(), CoreDistiller.varDoc(ref))),
      Doc.english("is already defined elsewhere")
    );
  }

  @Override public @NotNull Stage stage() {
    return Stage.RESOLVE;
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }
}
