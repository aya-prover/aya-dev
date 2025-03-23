// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve;

import kala.collection.immutable.ImmutableSeq;
import org.aya.resolve.context.Context;
import org.aya.resolve.module.ModuleLoader;
import org.aya.resolve.salt.Desalt;
import org.aya.resolve.salt.DesugarMisc;
import org.aya.resolve.visitor.StmtBinder;
import org.aya.resolve.visitor.StmtPreResolver;
import org.aya.resolve.visitor.StmtResolver;
import org.aya.syntax.concrete.stmt.Stmt;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class StmtResolvers {
  private final @NotNull ModuleLoader loader;
  private final @NotNull ResolveInfo info;
  private boolean hasError = false;

  public StmtResolvers(@NotNull ModuleLoader loader, @NotNull ResolveInfo info) {
    this.loader = loader;
    this.info = info;
  }

  private @NotNull ImmutableSeq<ResolvingStmt> fillContext(@NotNull ImmutableSeq<Stmt> stmts) {
    var resolver = new StmtPreResolver(loader, info);
    var result = resolver.resolveStmt(stmts, info.thisModule());
    this.hasError |= resolver.hasError();
    return result;
  }

  private void resolveStmts(@NotNull ImmutableSeq<ResolvingStmt> stmts) {
    var hasError = StmtResolver.resolveStmt(stmts, info);
    this.hasError |= hasError;
  }

  private void resolveBind(@NotNull ImmutableSeq<ResolvingStmt> stmts) {
    try {
      var binder = new StmtBinder(info);
      binder.resolveBind(stmts);
      info.opRename().forEachChecked((var, rename) ->
        binder.bind(rename.bindCtx(), rename.bind(), var));
    } catch (Context.ResolvingInterruptedException e) {
      this.hasError = true;
    }
  }

  public void desugar(@NotNull ImmutableSeq<Stmt> stmts) {
    var salt = new Desalt(info);
    stmts.forEach(stmt -> stmt.descentInPlace(salt, new DesugarMisc.Pat(info)));
  }

  /**
   * Resolve file level {@link Stmt}s
   */
  public void resolve(@NotNull ImmutableSeq<Stmt> stmts) {
    var resolving = fillContext(stmts);
    resolveStmts(resolving); // resolve mutates stmts
    resolveBind(resolving); // mutates bind blocks
    info.opSet().reportIfCyclic();
  }

  public boolean hasError() {
    return hasError;
  }
}
