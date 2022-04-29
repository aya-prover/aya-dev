// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import org.aya.core.term.Term;
import org.aya.pretty.doc.Doc;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;

public record BadLiteralPatternError(
  @NotNull SourcePos sourcePos,
  int integer,
  @NotNull Term type
) implements Problem {
  @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
    return Doc.vcat(Doc.english("The literal"),
      Doc.par(1, Doc.plain(String.valueOf(integer))),
      Doc.english("cannot be encoded as a term of type:"),
      Doc.par(1, type.toDoc(options)));
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }
}
