// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.actions;

import org.aya.concrete.Expr;
import org.aya.concrete.stmt.Decl;
import org.aya.concrete.stmt.Stmt;
import org.aya.concrete.visitor.EndoPattern;
import org.aya.concrete.visitor.StmtConsumer;
import org.aya.ide.util.XY;
import org.aya.lsp.utils.XYXY;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

/**
 * Ignore the traversal of definitions and large expressions when they don't contain the location.
 *
 * @author ice1000
 * @implNote This does not modify the AST.
 */
public interface SyntaxNodeAction<Location> extends StmtConsumer, EndoPattern {
  @NotNull Location location();
  boolean accept(@NotNull Location xy, @NotNull SourcePos sourcePos);

  @Override default void accept(@NotNull Stmt stmt) {
    if (!(stmt instanceof Decl decl) || accept(location(), decl.entireSourcePos()))
      StmtConsumer.super.accept(stmt);
  }

  @Override default @NotNull Expr apply(@NotNull Expr expr) {
    if (!accept(location(), expr.sourcePos())) return expr;
    return StmtConsumer.super.apply(expr);
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
