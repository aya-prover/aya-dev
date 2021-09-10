// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.error;

import org.aya.api.error.Problem;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.Var;
import org.aya.distill.BaseDistiller;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

public record CounterexampleError(@NotNull SourcePos sourcePos, @NotNull Var var) implements Problem {
  @Override public @NotNull Doc describe() {
    return Doc.sep(
      Doc.english("The counterexample"),
      BaseDistiller.varDoc(var),
      Doc.english("does not raise any type error"));
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }
}
