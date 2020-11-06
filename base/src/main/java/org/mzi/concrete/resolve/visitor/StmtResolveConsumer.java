// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.resolve.visitor;

import asia.kala.Unit;
import org.jetbrains.annotations.NotNull;
import org.mzi.concrete.Decl;
import org.mzi.concrete.Stmt;
import org.mzi.concrete.resolve.context.Context;
import org.mzi.concrete.resolve.context.SimpleContext;

/**
 * resolves expressions inside stmts, after StmtShallowResolveConsumer
 * @author re-xyr
 */
public final class StmtResolveConsumer implements Stmt.Visitor<@NotNull Context, Unit> {
  @Override
  public Unit visitCmd(Stmt.@NotNull CmdStmt cmd, @NotNull Context context) {
    return Unit.unit();
  }

  // Note that this function MUTATES the decl.
  @Override
  public Unit visitDataDecl(Decl.@NotNull DataDecl decl, @NotNull Context context) {
    throw new UnsupportedOperationException(); // TODO[xyr]: implement
  }

  // Note that this function MUTATES the decl.
  @Override
  public Unit visitFnDecl(Decl.@NotNull FnDecl decl, @NotNull Context context) {
    var local = new SimpleContext();
    local.setGlobal(context);
    ExprResolveFixpoint.INSTANCE.visitParams(decl.telescope, local);
    decl.result = decl.result.accept(ExprResolveFixpoint.INSTANCE, local);
    decl.body = decl.body.accept(ExprResolveFixpoint.INSTANCE, local);
    return Unit.unit();
  }
}
