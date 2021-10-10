// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import org.aya.api.distill.DistillerOptions;
import org.aya.api.error.Problem;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.Var;
import org.aya.distill.BaseDistiller;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

public record CounterexampleError(@Override @NotNull SourcePos sourcePos, @NotNull Var var) implements Problem {
  @Override public @NotNull Doc describe(DistillerOptions options) {
    return Doc.sep(
      Doc.english("The counterexample"),
      BaseDistiller.varDoc(var),
      Doc.english("does not raise any type error"));
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }
}
