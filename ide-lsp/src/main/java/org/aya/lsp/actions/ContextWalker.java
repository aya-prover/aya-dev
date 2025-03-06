// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.actions;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.ide.syntax.SyntaxNodeAction;
import org.aya.ide.util.XY;
import org.aya.syntax.concrete.Pattern;
import org.aya.syntax.concrete.stmt.Command;
import org.aya.syntax.concrete.stmt.ModuleName;
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.syntax.ref.AnyVar;
import org.aya.util.position.SourcePos;
import org.jetbrains.annotations.NotNull;

/// ContextWalker traversal the concrete syntax tree to target position, record all available variable.
public class ContextWalker implements SyntaxNodeAction.Cursor {
  public record ConcreteJdg(@NotNull AnyVar var, @NotNull Type userType) { }
  private final @NotNull MutableList<ConcreteJdg> localContext;
  private final @NotNull MutableList<String> moduleContext;
  private final @NotNull XY xy;

  public ContextWalker(@NotNull XY xy) {
    this.xy = xy;
    this.localContext = MutableList.create();
    this.moduleContext = MutableList.create();
  }

  // region Context Restriction

  @Override
  public void visitClause(Pattern.@NotNull Clause clause) {
    if (!accept(location(), clause.sourcePos)) return;
    Cursor.super.visitClause(clause);
  }

  // TODO: avoid traversal pattern matching clause that doesn't contain [xy].

  @Override
  public @NotNull XY location() {
    return xy;
  }

  @Override
  public void doAccept(@NotNull Stmt stmt) {
    if (stmt instanceof Command.Module module) {
      moduleContext.append(module.name());
    }

    Cursor.super.doAccept(stmt);
  }

  // endregion Context Restriction

  @Override
  public void visitVarDecl(@NotNull SourcePos pos, @NotNull AnyVar var, @NotNull Type type) {
    this.localContext.append(new ConcreteJdg(var, type));
  }

  /// @return all accessible local variables and their concrete types. The order is not guaranteed.
  /// FIXME: the order should be guaranteed, in order to handle shadowing.
  public @NotNull ImmutableSeq<ConcreteJdg> localContext() {
    return localContext.toSeq();
  }

  /// @return which module the cursor in
  public @NotNull ModuleName moduleContext() {
    return ModuleName.from(moduleContext);
  }
}
