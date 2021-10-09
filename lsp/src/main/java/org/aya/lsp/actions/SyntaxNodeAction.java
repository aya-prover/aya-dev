// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.actions;

import kala.collection.immutable.ImmutableSeq;
import kala.tuple.Unit;
import org.aya.concrete.Expr;
import org.aya.concrete.stmt.Signatured;
import org.aya.concrete.stmt.Stmt;
import org.aya.concrete.visitor.StmtConsumer;
import org.aya.lsp.utils.XY;
import org.jetbrains.annotations.NotNull;

/**
 * Ignore the traversal of definitions and large expressions when they don't contain the location.
 *
 * @author ice1000
 */
public interface SyntaxNodeAction extends StmtConsumer<XY> {
  @Override default void visitAll(@NotNull ImmutableSeq<@NotNull Stmt> stmts, XY xy) {
    stmts.forEach(stmt -> {
      if (!(stmt instanceof Signatured decl) || xy.inside(decl.entireSourcePos))
        stmt.accept(this, xy);
    });
  }

  @Override default Unit visitApp(@NotNull Expr.AppExpr expr, XY xy) {
    if (xy.inside(expr.sourcePos()))
      return StmtConsumer.super.visitApp(expr, xy);
    else return Unit.unit();
  }

  @Override default Unit visitNew(@NotNull Expr.NewExpr expr, XY xy) {
    if (xy.inside(expr.sourcePos()))
      return StmtConsumer.super.visitNew(expr, xy);
    else return Unit.unit();
  }
}
