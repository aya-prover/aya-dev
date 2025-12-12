// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve;

import kala.collection.immutable.ImmutableSeq;
import org.aya.resolve.module.ModuleLoader;
import org.aya.resolve.salt.Desalt;
import org.aya.resolve.salt.DesugarMisc;
import org.aya.resolve.visitor.StmtBinder;
import org.aya.resolve.visitor.StmtPreResolver;
import org.aya.resolve.visitor.StmtResolver;
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.util.reporter.LocalReporter;
import org.jetbrains.annotations.NotNull;

public final class StmtResolvers {
  private final @NotNull ModuleLoader loader;
  private final @NotNull ResolveInfo info;
  public final @NotNull LocalReporter reporter;

  public StmtResolvers(@NotNull ModuleLoader loader, @NotNull ResolveInfo info) {
    this.loader = loader;
    this.info = info;
    reporter = new LocalReporter(info.reporter());
  }

  private @NotNull ImmutableSeq<ResolvingStmt> fillContext(@NotNull ImmutableSeq<Stmt> stmts) {
    var resolver = new StmtPreResolver(loader, info);
    var resolving = resolver.resolveStmt(stmts, info.thisModule());
    info.commands().appendAll(resolving.view().mapNotNull(it ->
      it instanceof ResolvingStmt.ResolvingCmd cmd
        ? cmd.cmd()
        : null));
    return resolving;
  }

  private void resolveStmts(@NotNull ImmutableSeq<ResolvingStmt> stmts) {
    StmtResolver.resolveStmt(stmts, info, reporter);
  }

  private void resolveBind(@NotNull ImmutableSeq<ResolvingStmt> stmts) {
    var binder = new StmtBinder(info, reporter);
    binder.resolveBind(stmts);
    info.opRename().forEach((var, rename) ->
      binder.bind(rename.bindCtx(), rename.bind(), var));
  }

  public void desugar(@NotNull ImmutableSeq<Stmt> stmts) {
    var salt = new Desalt(info, reporter);
    stmts.forEach(stmt -> stmt.descentInPlace(salt, new DesugarMisc.Pat(info, reporter)));
  }

  /// Resolve file level [Stmt]s
  public void resolve(@NotNull ImmutableSeq<Stmt> stmts) {
    var resolving = fillContext(stmts);
    resolveStmts(resolving); // resolve mutates stmts
    resolveBind(resolving); // mutates bind blocks
    info.opSet().reportIfCyclic();
  }
}
