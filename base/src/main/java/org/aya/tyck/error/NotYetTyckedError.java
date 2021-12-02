// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import org.aya.api.distill.DistillerOptions;
import org.aya.api.error.Problem;
import org.aya.api.ref.Var;
import org.aya.distill.BaseDistiller;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

public record NotYetTyckedError(@Override @NotNull SourcePos sourcePos, @NotNull Var var) implements Problem {
  @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
    return Doc.sep(Doc.english("Attempting to use a definition"),
      Doc.styled(Style.code(), BaseDistiller.varDoc(var)),
      Doc.english("which is not yet typechecked"));
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }
}
