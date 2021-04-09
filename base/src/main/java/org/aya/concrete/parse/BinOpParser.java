// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.parse;

import org.aya.concrete.Expr;
import org.glavo.kala.collection.SeqView;
import org.jetbrains.annotations.NotNull;

public record BinOpParser(@NotNull SeqView<@NotNull Elem> seq) {
  @NotNull Expr build() {
    return null;
  }

  /**
   * something like {@link org.aya.api.util.Arg<Expr>}
   * but only used in binary operator building
   */
  public record Elem(@NotNull Expr expr, boolean explicit) {
  }
}
