// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.hatate;

import kala.collection.immutable.ImmutableSeq;
import kala.value.Ref;
import org.aya.api.ref.Var;
import org.aya.api.util.NormalizeMode;
import org.aya.concrete.Expr;
import org.aya.concrete.visitor.ExprFixpoint;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ice1000
 */
public sealed interface Literate {
  record Raw(@NotNull String content) implements Literate {
  }

  record Err(@NotNull Ref<Var> def) implements Literate {
  }

  /**
   * @param isType <code>true</code> if show type, otherwise show the term itself
   * @param mode   <code>null</code> if as-is
   */
  record CodeCmd(boolean isType, @Nullable NormalizeMode mode) {
  }

  record Code(@NotNull Ref<Expr> expr, @NotNull CodeCmd cmd) implements Literate {
    @Contract(mutates = "this")
    public <P> void modify(@NotNull ExprFixpoint<P> fixpoint, P p) {
      expr.set(expr.value.accept(fixpoint, p));
    }
  }

  record Par(@NotNull ImmutableSeq<Literate> children) implements Literate {
  }
}
