// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.error;

import org.aya.distill.BaseDistiller;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.aya.ref.AnyVar;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

public record DuplicateNameError(
  @NotNull String name, @NotNull AnyVar ref,
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
