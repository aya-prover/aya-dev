// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.visitor;

import org.aya.concrete.Expr;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public interface ExprConsumer extends Consumer<Expr>, PatternConsumer {
  default void pre(@NotNull Expr expr) {}

  default void post(@NotNull Expr expr) {}

  default void accept(@NotNull Expr expr) {
    pre(expr);
    switch (expr) {
      case Expr.Match match -> match.descent(
        e -> {
          accept(e);
          return e;
        },
        p -> {
          accept(p);
          return p;
        }
      );
      case Expr ex -> ex.descent(e -> {
        accept(e);
        return e;
      });
    }
    post(expr);
  }
}
