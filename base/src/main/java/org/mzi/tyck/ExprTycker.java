// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.tyck;

import org.jetbrains.annotations.NotNull;
import org.mzi.concrete.Expr;
import org.mzi.core.term.Term;

public class ExprTycker implements Expr.BaseVisitor<Term, ExprTycker.Result> {
  @Override
  public Result catchAll(@NotNull Expr expr, Term term) {
    throw new UnsupportedOperationException(expr.toString());
  }

  public static class Result {
  }
}
