// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.visitor;

import org.aya.concrete.Expr;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public interface EndoExpr extends Function<Expr, Expr> {
  default @NotNull Expr pre(@NotNull Expr expr) {
    return expr;
  }

  default @NotNull Expr post(@NotNull Expr expr) {
    return expr;
  }

  default @NotNull Expr apply(@NotNull Expr expr) {
    return post(pre(expr).descent(this));
  }
}
