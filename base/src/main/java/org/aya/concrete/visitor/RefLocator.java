// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.visitor;

import org.aya.api.error.SourcePos;
import org.aya.concrete.Expr;
import org.glavo.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.NotNull;

/**
 * TODO: use StmtConsumer (which is not yet implemented) instead
 * @author ice1000
 */
public class RefLocator implements StmtFixpoint<RefLocator.XY> {
  public final @NotNull Buffer<SourcePos> locations = Buffer.create();

  @Override public @NotNull Expr visitRef(@NotNull Expr.RefExpr expr, XY xy) {
    check(xy, expr.sourcePos());
    return StmtFixpoint.super.visitRef(expr, xy);
  }

  @Override public @NotNull Expr visitProj(@NotNull Expr.ProjExpr expr, XY xy) {
    if (expr.ix().isRight()) check(xy, expr.ix().getRightValue().sourcePos());
    return StmtFixpoint.super.visitProj(expr, xy);
  }

  private void check(@NotNull XY xy, @NotNull SourcePos sourcePos) {
    if (sourcePos.contains(xy.x, xy.y)) locations.append(sourcePos);
  }

  public static record XY(int x, int y) {
  }
}
