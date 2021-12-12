// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.terck.error;

import org.aya.api.distill.DistillerOptions;
import org.aya.api.error.Problem;
import org.aya.core.def.Def;
import org.aya.core.term.Term;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.aya.terck.Behavior;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record NonTerminating(@NotNull SourcePos sourcePos, @NotNull String name,
                             @Nullable Behavior.Diag<Def, Term.Param> diag) implements Problem {
  @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
    return Doc.sep(Doc.english("The recursive definition"),
      Doc.styled(Style.code(), name),
      Doc.english("is not structurally recursive"));
  }

  @Override public @NotNull Doc hint(@NotNull DistillerOptions options) {
    if (diag == null) return Doc.empty();
    var matrix = diag.matrix();
    return Doc.vcat(
      Doc.english("In particular, the problematic call is:"),
      Doc.nest(2, matrix.callTerm().toDoc(options)),
      Doc.english("whose call matrix is:"),
      Doc.nest(2, matrix.toDoc()),
      Doc.english("whose diagonal is:"),
      Doc.nest(2, diag.toDoc())
    );
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }
}
