// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import org.aya.prettier.BasePrettier;
import org.aya.pretty.doc.Doc;
import org.aya.ref.AnyVar;
import org.aya.util.prettier.PrettierOptions;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

public record CounterexampleError(@Override @NotNull SourcePos sourcePos, @NotNull AnyVar var) implements TyckError {
  @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
    return Doc.sep(
      Doc.english("The counterexample"),
      BasePrettier.varDoc(var),
      Doc.english("does not raise any type error"));
  }
}
