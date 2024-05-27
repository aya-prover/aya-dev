// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.normalize.error;

import org.aya.pretty.doc.Doc;
import org.aya.syntax.core.term.repr.MetaLitTerm;
import org.aya.util.error.SourcePos;
import org.aya.util.prettier.PrettierOptions;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;

public record UnsolvedLit(
  @NotNull MetaLitTerm lit
) implements Problem {
  @Override public @NotNull SourcePos sourcePos() { return lit.sourcePos(); }
  @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
    return Doc.vcat(
      Doc.english("Unable to solve the type of this literal:"),
      Doc.par(1, lit.toDoc(options)),
      Doc.plain("I'm confused about the following candidates:"),
      Doc.par(1, Doc.join(Doc.plain(", "), lit.candidates().map(d -> Doc.code(d.def().name()))))
    );
  }

  @Override public @NotNull Severity level() { return Severity.ERROR; }
}
