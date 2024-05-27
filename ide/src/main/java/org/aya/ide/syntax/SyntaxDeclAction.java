// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ide.syntax;

import org.aya.syntax.concrete.stmt.Command;
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.syntax.concrete.stmt.StmtVisitor;
import org.jetbrains.annotations.NotNull;

/**
 * Traverse only definitions' name and commands.
 *
 * @author kiva
 * @implNote This does not modify the AST.
 */
public interface SyntaxDeclAction extends StmtVisitor {
  @Override default void accept(@NotNull Stmt stmt) {
    if (stmt instanceof Command.Module module)
      StmtVisitor.super.accept(module);
    // should not call super on other cases
  }
}
