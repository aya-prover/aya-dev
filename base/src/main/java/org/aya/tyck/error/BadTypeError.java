// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.error;

import org.aya.api.error.Problem;
import org.aya.concrete.Expr;
import org.aya.core.term.Term;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

public record BadTypeError(
  @NotNull Expr expr,
  @NotNull Doc expectedType,
  @NotNull Term actualType
) implements Problem.Error, TyckProblem {
  @Override public @NotNull Doc describe() {
    return Doc.hcat(
      Doc.plain("The expected type `"),
      actualType.toDoc(),
      Doc.plain("` is not a "),
      expectedType,
      Doc.plain(", therefore cannot type a term such as `"),
      expr.toDoc(),
      Doc.plain("`")
    );
  }
}
