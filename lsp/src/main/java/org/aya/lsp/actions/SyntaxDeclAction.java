// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.actions;

import org.aya.concrete.stmt.Command;
import org.aya.concrete.stmt.Stmt;
import org.aya.concrete.stmt.TelescopicDecl;
import org.aya.concrete.stmt.TopLevelDecl;
import org.aya.concrete.visitor.StmtOps;
import org.jetbrains.annotations.NotNull;

/**
 * Traverse only definitions' name and commands.
 *
 * @author kiva
 * @implNote This does not modify the AST.
 */
public interface SyntaxDeclAction<P> extends StmtOps<P> {
  @Override default void visit(@NotNull Stmt stmt, P pp) {
    switch (stmt) {
      case TelescopicDecl decl -> visitDecl(decl, pp);
      case Command cmd -> visitCommand(cmd, pp);
      case Stmt misc -> {}
    }
  }

  @Override default void visitDecl(@NotNull TopLevelDecl decl, P pp) {
    // should not call super
  }
}
