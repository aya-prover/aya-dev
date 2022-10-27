// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import org.aya.core.term.ErasedTerm;
import org.aya.pretty.doc.Doc;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

public record ErasedError(
  @NotNull SourcePos sourcePos,
  @NotNull ErasedTerm wellTyped
) implements TyckError {
  @Override public @NotNull SourcePos sourcePos() {
    return wellTyped.sourcePos() == null ? sourcePos : wellTyped.sourcePos();
  }

  @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
    return Doc.sepNonEmpty(Doc.plain("The"),
      wellTyped.type().toDoc(options),
      Doc.english("term is expected to be not erased"));
  }
}
