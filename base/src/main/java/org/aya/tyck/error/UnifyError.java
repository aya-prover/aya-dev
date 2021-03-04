// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.error;

import org.aya.api.error.Problem;
import org.aya.concrete.Expr;
import org.aya.core.term.Term;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

public record UnifyError(
  @NotNull Expr expr,
  @NotNull Term expected,
  @NotNull Term actual
) implements Problem.Error, TyckProblem {
  @Override public @NotNull Doc describe() {
    return Doc.hcat(
      Doc.plain("The expected type `"),
      expected.toDoc(),
      Doc.plain("` does not match the actual type `"),
      actual.toDoc(),
      Doc.plain("`")
    );
  }
}
