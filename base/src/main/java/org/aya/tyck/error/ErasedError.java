// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import org.aya.core.term.Term;
import org.aya.pretty.doc.Doc;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record ErasedError(
  @Override @NotNull SourcePos sourcePos,
  @NotNull Term wellTyped, @Nullable Term type) implements TyckError {
  @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
    if (type == null) {
      return Doc.sepNonEmpty(Doc.english("The term is expected to be not erased"),
        wellTyped.toDoc(options));
    } else {
      return Doc.sepNonEmpty(Doc.english("The term is expected to be not erased"),
        wellTyped.toDoc(options),
        Doc.symbol(":"),
        type.toDoc(options));
    }
  }
}
