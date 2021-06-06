// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.lsp.definition;

import kala.collection.mutable.Buffer;
import kala.tuple.Unit;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.DefVar;
import org.aya.api.ref.Var;
import org.aya.api.util.WithPos;
import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.concrete.visitor.StmtConsumer;
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

  @Override public @NotNull Unit visitProj(@NotNull Expr.ProjExpr expr, XY xy) {
    if (expr.ix().isRight()) {
      var pos = expr.ix().getRightValue();
      check(xy, pos.sourcePos(), expr.resolvedIx().get());
    }
    return StmtConsumer.super.visitProj(expr, xy);
  }

  @Override public Unit visitError(Expr.@NotNull ErrorExpr error, XY xy) {
    return Unit.unit();
  }

  @Override public Unit visitBind(@NotNull Pattern.Bind bind, XY xy) {
    if (bind.resolved().value instanceof DefVar<?, ?> defVar)
      check(xy, bind.sourcePos(), defVar);
    return StmtConsumer.super.visitBind(bind, xy);
  }

  @Override public Unit visitCtor(@NotNull Pattern.Ctor ctor, XY xy) {
    if (ctor.resolved().value != null)
      check(xy, ctor.name().sourcePos(), ctor.resolved().get());
    return StmtConsumer.super.visitCtor(ctor, xy);
  }

  private void check(@NotNull XY xy, @NotNull SourcePos sourcePos, Var var) {
    if (sourcePos.contains(xy.x, xy.y)) locations.append(new WithPos<>(sourcePos, var));
  }

  public static record XY(int x, int y) {
  }
}
