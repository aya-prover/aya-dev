// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.resolve.visitor;

import org.glavo.kala.Unit;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.MutableHashMap;
import org.jetbrains.annotations.NotNull;
import org.mzi.concrete.Decl;
import org.mzi.concrete.Stmt;
import org.mzi.concrete.resolve.context.Context;
import org.mzi.concrete.resolve.context.ModuleContext;
import org.mzi.concrete.resolve.error.ModNotFoundError;
import org.mzi.concrete.resolve.module.ModuleLoader;

/**
 * simply adds all top-level names to the context
 *
 * @author re-xyr
 */
public final record StmtShallowResolver(@NotNull ModuleLoader loader)
  implements Stmt.Visitor<@NotNull ModuleContext, Unit> {
  @Override
  public Unit visitModule(Stmt.@NotNull ModuleStmt mod, @NotNull ModuleContext context) {
    var newCtx = context.derive();
    visitAll(mod.contents(), newCtx);
    context.importModule(ImmutableSeq.of(mod.name()), mod.accessibility(), newCtx.exports(), mod.sourcePos());
    return Unit.unit();
  }

  @Override
  public Unit visitCmd(Stmt.@NotNull CmdStmt cmd, @NotNull ModuleContext context) {
    switch (cmd.cmd()) {
      case Open ->
        context.openModule(
          cmd.path(),
          cmd.accessibility(),
          cmd.useHide()::uses,
          MutableHashMap.of(), // TODO handle renaming
          cmd.sourcePos()
        );
      case Import -> {
        var success = loader.load(cmd.path());
        if (success == null) context.reportAndThrow(new ModNotFoundError(cmd.path(), cmd.sourcePos()));
        context.importModule(cmd.path(), Stmt.Accessibility.Private, success, cmd.sourcePos());
      }
    }
    return Unit.unit();
  }

  private Unit visitDecl(@NotNull Decl decl, @NotNull ModuleContext context) {
    context.addGlobal(
      Context.TOP_LEVEL_MOD_NAME,
      decl.ref().name(),
      decl.accessibility(),
      decl.ref(),
      decl.sourcePos()
    );
    decl.ctx = context;
    return Unit.unit();
  }

  @Override
  public Unit visitDataDecl(Decl.@NotNull DataDecl decl, @NotNull ModuleContext context) {
    return visitDecl(decl, context);
  }

  @Override
  public Unit visitFnDecl(Decl.@NotNull FnDecl decl, @NotNull ModuleContext context) {
    // TODO[xyr]: abuse block currently have no use, so we ignore it for now.
    return visitDecl(decl, context);
  }
}
