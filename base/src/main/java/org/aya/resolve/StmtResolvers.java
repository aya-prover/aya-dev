// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve;

import kala.collection.immutable.ImmutableSeq;
import org.aya.resolve.module.ModuleLoader;
import org.aya.resolve.salt.Desalt;
import org.aya.resolve.visitor.StmtBinder;
import org.aya.resolve.visitor.StmtPreResolver;
import org.aya.resolve.visitor.StmtResolver;
import org.aya.syntax.concrete.stmt.Stmt;
import org.jetbrains.annotations.NotNull;

public record StmtResolvers(@NotNull ModuleLoader loader, @NotNull ResolveInfo info) {
  private @NotNull ImmutableSeq<ResolvingStmt> fillContext(@NotNull ImmutableSeq<Stmt> stmts) {
    return new StmtPreResolver(loader, info).resolveStmt(stmts, info.thisModule());
  }

  private void resolveStmts(@NotNull ImmutableSeq<ResolvingStmt> stmts) {
    StmtResolver.resolveStmt(stmts, info);
  }

  private void resolveBind(@NotNull ImmutableSeq<ResolvingStmt> stmts) {
    var binder = new StmtBinder(info);
    binder.resolveBind(stmts);
    info.opRename().forEach((var, rename) ->
      binder.bind(rename.bindCtx(), rename.bind(), var));
  }

  private void desugar(@NotNull ImmutableSeq<Stmt> stmts) {
    var salt = new Desalt(info);
    stmts.forEach(stmt -> stmt.descentInPlace(salt, salt.pattern()));
  }

  /**
   * Resolve file level {@link Stmt}s
   */
  public void resolve(@NotNull ImmutableSeq<Stmt> stmts) {
    var resolving = fillContext(stmts);
    resolveStmts(resolving); // resolve mutates stmts
    resolveBind(resolving); // mutates bind blocks
    desugar(stmts);
    info.opSet().reportIfCyclic();
  }
}
