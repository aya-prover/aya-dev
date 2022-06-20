// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.actions;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.Expr;
import org.aya.concrete.stmt.BaseDecl;
import org.aya.concrete.stmt.Stmt;
import org.aya.concrete.visitor.StmtOps;
import org.aya.lsp.utils.XY;
import org.aya.lsp.utils.XYXY;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

/**
 * Ignore the traversal of definitions and large expressions when they don't contain the location.
 *
 * @author ice1000
 * @implNote This does not modify the AST.
 */
public interface SyntaxNodeAction<P> extends StmtOps<P> {
  boolean accept(@NotNull P xy, @NotNull SourcePos sourcePos);

  default void visitAll(@NotNull ImmutableSeq<@NotNull Stmt> stmts, P xy) {
    stmts.forEach(stmt -> {
      if (!(stmt instanceof BaseDecl decl) || accept(xy, decl.entireSourcePos))
        visit(stmt, xy);
    });
  }

  @Override default @NotNull Expr visitExpr(@NotNull Expr expr, P xy) {
    if (!accept(xy, expr.sourcePos())) return expr;
    return StmtOps.super.visitExpr(expr, xy);
  }

  /** Need to visit the decl/expr placed at the cursor position XY */
  interface Cursor extends SyntaxNodeAction<XY> {
    @Override default boolean accept(@NotNull XY xy, @NotNull SourcePos sourcePos) {
      return xy.inside(sourcePos);
    }
  }

  /** Need to visit all decls inside XYXY range */
  interface Ranged extends SyntaxNodeAction<XYXY> {
    @Override default boolean accept(@NotNull XYXY xy, @NotNull SourcePos sourcePos) {
      return xy.contains(sourcePos);
    }
  }
}
