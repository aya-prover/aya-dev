// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.resolve.visitor;

import asia.kala.Unit;
import org.jetbrains.annotations.NotNull;
import org.mzi.concrete.Decl;
import org.mzi.concrete.Stmt;
import org.mzi.concrete.resolve.context.Context;
import org.mzi.concrete.resolve.context.ModuleContext;
import org.mzi.concrete.resolve.module.ModuleLoader;

/**
 * simply adds all top-level names to the context
 *
 * @author re-xyr
 */
public final record StmtShallowResolveConsumer(@NotNull ModuleLoader loader)
  implements Stmt.Visitor<@NotNull Context, Unit> {

  @Override
  public Unit visitCmd(Stmt.@NotNull CmdStmt cmd, @NotNull Context context) {
    switch (cmd.cmd()) {
      case Open -> {
        var preMod = context.getModule(cmd.path());
        if (preMod == null)
          throw new IllegalStateException("Opening non-existing module `" + cmd.path().joinToString(".") + "`"); // TODO[xyr]: report instead of throw
        var mod = new ModuleContext(preMod, cmd.useHide());
        mod.forEachLocal((name, data) -> {
          if (cmd.useHide().uses(name)) {
            if (data._2 == Stmt.Accessibility.Public) context.putLocal(name, data._1, cmd.accessibility());
            else
              throw new IllegalStateException("Access to private name `" + name + "`"); // TODO[xyr]: report instead of throw
          }
        });
      }
      case Import -> {
        var success = loader.loadIntoContext(context, cmd.path(), cmd.useHide());
        if (!success)
          throw new IllegalStateException("Importing non-existing module `" + cmd.path().joinToString(".") + "`"); // TODO[xyr]: report instead of throw
      }
    }
    return Unit.unit();
  }

  private Unit visitDecl(@NotNull Decl decl, @NotNull Context context) {
    context.putLocal(decl.ref().name(), decl.ref(), decl.accessibility());
    return Unit.unit();
  }

  @Override
  public Unit visitDataDecl(Decl.@NotNull DataDecl decl, @NotNull Context context) {
    visitAll(decl.abuseBlock.toImmutableSeq(), context);
    return visitDecl(decl, context);
  }

  @Override
  public Unit visitFnDecl(Decl.@NotNull FnDecl decl, @NotNull Context context) {
    visitAll(decl.abuseBlock.toImmutableSeq(), context);
    return visitDecl(decl, context);
  }
}
