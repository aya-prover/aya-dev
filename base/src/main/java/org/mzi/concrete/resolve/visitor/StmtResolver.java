// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.resolve.visitor;

import org.glavo.kala.Unit;
import org.jetbrains.annotations.NotNull;
import org.mzi.concrete.Decl;
import org.mzi.concrete.Stmt;
import org.mzi.concrete.resolve.context.Context;
import org.mzi.concrete.resolve.context.SimpleContext;

/**
 * resolves expressions inside stmts, after StmtShallowResolveConsumer
 * @author re-xyr
 */
public final class StmtResolver implements Stmt.Visitor<@NotNull Context, Unit> {
  public static final @NotNull StmtResolver INSTANCE = new StmtResolver();

  private StmtResolver() {
  }

  @Override
  public Unit visitModule(Stmt.@NotNull ModuleStmt mod, @NotNull Context context) {
    throw new UnsupportedOperationException(); // TODO[xyr]: implement
  }

  @Override
  public Unit visitCmd(Stmt.@NotNull CmdStmt cmd, @NotNull Context context) {
    return Unit.unit();
  }

  /** @apiNote Note that this function MUTATES the decl. */
  @Override
  public Unit visitDataDecl(Decl.@NotNull DataDecl decl, @NotNull Context context) {
    throw new UnsupportedOperationException(); // TODO[xyr]: implement
  }

  /** @apiNote Note that this function MUTATES the decl. */
  @Override
  public Unit visitFnDecl(Decl.@NotNull FnDecl decl, @NotNull Context context) {
    var local = new SimpleContext();
    local.setOuterContext(context);
    decl.telescope = ExprResolver.INSTANCE.visitParams(decl.telescope, local);
    decl.result = decl.result.resolve(local);
    decl.body = decl.body.resolve(local);
    return Unit.unit();
  }
}
