// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.visitor;

import org.aya.api.error.SourcePos;
import org.aya.api.ref.Var;
import org.aya.api.util.WithPos;
import org.aya.concrete.Expr;
import org.glavo.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.NotNull;

/**
 * TODO: use StmtConsumer (which is not yet implemented) instead
 * @author ice1000
 */
public class RefLocator implements StmtFixpoint<RefLocator.XY> {
  public final @NotNull Buffer<WithPos<Var>> locations = Buffer.create();

  @Override public @NotNull Expr visitRef(@NotNull Expr.RefExpr expr, XY xy) {
    check(xy, expr.sourcePos(), expr.resolvedVar());
    return StmtFixpoint.super.visitRef(expr, xy);
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
