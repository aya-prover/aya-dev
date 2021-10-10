// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.resolve.error;

import org.aya.api.distill.DistillerOptions;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.Var;
import org.aya.distill.BaseDistiller;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.jetbrains.annotations.NotNull;

public record DuplicateNameError(
  @NotNull String name, @NotNull Var ref,
  @Override @NotNull SourcePos sourcePos
) implements ResolveProblem {
  @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
    return Doc.sep(
      Doc.english("The name"),
      Doc.plain(name),
      Doc.parened(Doc.styled(Style.code(), BaseDistiller.varDoc(ref))),
      Doc.english("is already defined elsewhere")
    );
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }
}
