// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import org.aya.prettier.BasePrettier;
import org.aya.pretty.doc.Doc;
import org.aya.util.PrettierOptions;
import org.aya.util.position.SourcePos;
import org.jetbrains.annotations.NotNull;

public record TailRecError(
  @Override @NotNull SourcePos sourcePos
) implements TyckError {
  @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
    return Doc.sep(Doc.english("Function marked as"),
      Doc.code(Doc.styled(BasePrettier.KEYWORD, "tailrec")),
      Doc.english("is not tail-recursive"));
  }
}
