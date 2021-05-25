// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.lsp.definition;

import org.aya.api.error.SourcePos;
import org.aya.api.ref.Var;
import org.aya.api.util.WithPos;
import org.aya.concrete.Expr;
import org.aya.concrete.visitor.StmtConsumer;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000, kiva
 */
public class RefLocator implements StmtConsumer<RefLocator.XY> {
  public final @NotNull Buffer<WithPos<Var>> locations = Buffer.of();

  @Override public @NotNull Unit visitRef(@NotNull Expr.RefExpr expr, XY xy) {
    check(xy, expr.sourcePos(), expr.resolvedVar());
    StmtConsumer.super.visitRef(expr, xy);
    return Unit.unit();
  }

  /*
  @Override public @NotNull Expr visitProj(@NotNull Expr.ProjExpr expr, XY xy) {
    if (expr.ix().isRight()) {
      var pos = expr.ix().getRightValue();
      check(xy, pos.sourcePos(), var);
    }
    return StmtFixpoint.super.visitProj(expr, xy);
  }
  */

  private void check(@NotNull XY xy, @NotNull SourcePos sourcePos, Var var) {
    if (sourcePos.contains(xy.x, xy.y)) locations.append(new WithPos<>(sourcePos, var));
  }

  public static record XY(int x, int y) {
  }
}
