// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import org.aya.concrete.stmt.TeleDecl;
import org.aya.core.term.Term;
import org.aya.pretty.doc.Doc;
import org.aya.util.error.SourcePos;
import org.aya.util.prettier.PrettierOptions;
import org.jetbrains.annotations.NotNull;

public record NonPositiveDataError(
  @Override @NotNull SourcePos sourcePos,
  @NotNull TeleDecl.DataCtor ctor,
  @NotNull Term term
) implements TyckError {
  @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
    return Doc.sep(
      Doc.english("Non-positive recursive occurrence of data type "),
      ctor.dataRef.concrete.toDoc(options),
      Doc.english(" in constructor "),
      ctor.toDoc(options),
      Doc.english(" at "),
      term.toDoc(options));
  }
}
