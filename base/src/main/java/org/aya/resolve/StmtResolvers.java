// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve;

import kala.collection.immutable.ImmutableSeq;
import kala.value.MutableValue;
import kala.value.primitive.MutableBooleanValue;
import org.aya.resolve.context.Context;
import org.aya.resolve.module.ModuleLoader;
import org.aya.resolve.salt.Desalt;
import org.aya.resolve.salt.DesugarMisc;
import org.aya.resolve.visitor.StmtBinder;
import org.aya.resolve.visitor.StmtPreResolver;
import org.aya.resolve.visitor.StmtResolver;
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.util.HasError;
import org.jetbrains.annotations.NotNull;

public final class StmtResolvers implements HasError {
  private final @NotNull ModuleLoader loader;
  private final @NotNull ResolveInfo info;
  private boolean hasError = false;

  public StmtResolvers(@NotNull ModuleLoader loader, @NotNull ResolveInfo info) {
    this.loader = loader;
    this.info = info;
  }

  @Override public void foundError() {
    hasError = true;
  }

  @Override public boolean hasError() {
    return hasError;
  }

  private @NotNull ImmutableSeq<ResolvingStmt> fillContext(@NotNull ImmutableSeq<Stmt> stmts) {
    var resolver = new StmtPreResolver(loader, info, this);
    return resolver.resolveStmt(stmts, info.thisModule());
  }

  private void resolveStmts(@NotNull ImmutableSeq<ResolvingStmt> stmts) {
    StmtResolver.resolveStmt(stmts, info, this);
  }

  private void resolveBind(@NotNull ImmutableSeq<ResolvingStmt> stmts) {
    try {
      var binder = new StmtBinder(info);
      binder.resolveBind(stmts);
      info.opRename().forEachChecked((var, rename) ->
        binder.bind(rename.bindCtx(), rename.bind(), var));
    } catch (Context.ResolvingInterruptedException e) {
      foundError();
    }
  }

  public void desugar(@NotNull ImmutableSeq<Stmt> stmts) {
    var salt = new Desalt(info);
    try {
      stmts.forEach(stmt -> stmt.descentInPlace(salt, new DesugarMisc.Pat(info)));
    } catch (RuntimeException e) {
      if (e.getCause() instanceof Context.ResolvingInterruptedException) {
        foundError();
      } else {
        throw e;
      }
    }
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
}
