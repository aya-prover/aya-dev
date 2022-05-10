// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.actions;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.Expr;
import org.aya.concrete.stmt.Signatured;
import org.aya.concrete.stmt.Stmt;
import org.aya.concrete.visitor.StmtOps;
import org.aya.lsp.utils.XY;
import org.jetbrains.annotations.NotNull;

/**
 * Ignore the traversal of definitions and large expressions when they don't contain the location.
 *
 * @author ice1000
 * @implNote This does not modify the AST.
 */
public interface SyntaxNodeAction extends StmtOps<XY> {
  default void visitAll(@NotNull ImmutableSeq<@NotNull Stmt> stmts, XY xy) {
    stmts.forEach(stmt -> {
      if (!(stmt instanceof Signatured decl) || xy.inside(decl.entireSourcePos))
        visit(stmt, xy);
    });
  }

  @Override default @NotNull Expr visitExpr(@NotNull Expr expr, XY pp) {
    if (!pp.inside(expr.sourcePos())) return expr;
    return StmtOps.super.visitExpr(expr, pp);
  }
}
