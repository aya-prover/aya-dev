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

public record GeneralizedNotAvailableError(
  @Override @NotNull SourcePos sourcePos, @NotNull AnyVar var
) implements ResolveProblem {
  @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
    return Doc.sep(
      Doc.english("The generalized variable"),
      Doc.styled(Style.code(), BaseDistiller.varDoc(var)),
      Doc.english("is not available here")
    );
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }
}
