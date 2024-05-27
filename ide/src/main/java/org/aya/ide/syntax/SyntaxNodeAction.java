// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ide.syntax;

import org.aya.ide.util.XY;
import org.aya.ide.util.XYXY;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.syntax.concrete.stmt.StmtVisitor;
import org.aya.syntax.concrete.stmt.decl.Decl;
import org.aya.util.error.PosedConsumer;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

/**
 * Ignore the traversal of definitions and large expressions when they don't contain the location.
 *
 * @author ice1000
 * @implNote This does not modify the AST.
 */
public interface SyntaxNodeAction<Location> extends StmtVisitor, PosedConsumer<Expr> {
  @NotNull Location location();
  boolean accept(@NotNull Location xy, @NotNull SourcePos sourcePos);

  @Override default void accept(@NotNull Stmt stmt) {
    if (!(stmt instanceof Decl decl) || accept(location(), decl.entireSourcePos()))
      StmtVisitor.super.accept(stmt);
  }

  @Override default void accept(SourcePos sourcePos, Expr expr) {
    if (!accept(location(), sourcePos)) return;
    expr.forEach(this);
  }

  /**
   * Need to visit the decl/expr placed at the cursor position XY
   */
  interface Cursor extends SyntaxNodeAction<XY> {
    @Override default boolean accept(@NotNull XY xy, @NotNull SourcePos sourcePos) {
      return xy.inside(sourcePos);
    }
  }

  /**
   * Need to visit all decls inside XYXY range
   */
  interface Ranged extends SyntaxNodeAction<XYXY> {
    @Override default boolean accept(@NotNull XYXY xy, @NotNull SourcePos sourcePos) {
      return xy.contains(sourcePos);
    }
  }
}
