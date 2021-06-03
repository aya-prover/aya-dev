// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.error;

import org.aya.api.error.Problem;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.Var;
import org.aya.core.visitor.CoreDistiller;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;

public record MissingFieldError(
  @NotNull SourcePos sourcePos,
  @NotNull ImmutableSeq<Var> missing
) implements Problem {
  @Override public @NotNull Doc describe() {
    return Doc.hcat(
      Doc.plain("Missing field(s): "),
      Doc.join(Doc.plain(", "), missing.stream()
        .map(CoreDistiller::varDoc)
        .map(m -> Doc.styled(Style.code(), m)))
    );
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }
}
