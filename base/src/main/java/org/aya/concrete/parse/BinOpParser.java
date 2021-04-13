// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.parse;

import org.aya.api.error.SourcePos;
import org.aya.api.util.Arg;
import org.aya.concrete.Expr;
import org.glavo.kala.collection.SeqView;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public record BinOpParser(@NotNull SeqView<@NotNull Elem> seq) {
  @NotNull public Expr build(SourcePos sourcePos) {
    // TODO: implement
    return new Expr.AppExpr(
      sourcePos,
      seq.first().expr(),
      seq.view().drop(1)
        .map(e -> new Arg<>(e.expr(), e.explicit()))
        .toImmutableSeq()
    );
  }

  /**
   * something like {@link org.aya.api.util.Arg<Expr>}
   * but only used in binary operator building
   */
  public record Elem(@NotNull Expr expr, boolean explicit) {
    public @NotNull Elem mapExpr(@NotNull Function<Expr, Expr> map) {
      return new Elem(map.apply(expr), explicit);
    }
  }
}
