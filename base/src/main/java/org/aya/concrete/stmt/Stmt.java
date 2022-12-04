// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.stmt;

import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.desugar.Desugarer;
import org.aya.distill.ConcreteDistiller;
import org.aya.generic.AyaDocile;
import org.aya.pretty.doc.Doc;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.module.ModuleLoader;
import org.aya.resolve.visitor.StmtResolver;
import org.aya.resolve.visitor.StmtShallowResolver;
import org.aya.tyck.order.TyckUnit;
import org.aya.util.distill.DistillerOptions;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * @author kiva
 */
public sealed interface Stmt extends AyaDocile, TyckUnit permits Decl, Command, Generalize {
  /** @apiNote the \import stmts do not have a meaningful accessibility, do not refer to this in those cases */
  @Contract(pure = true) @NotNull Accessibility accessibility();

  @Contract(mutates = "param1")
  static void resolve(@NotNull ImmutableSeq<Stmt> statements, @NotNull ResolveInfo resolveInfo, @NotNull ModuleLoader loader) {
    resolveWithoutDesugar(statements, resolveInfo, loader);
    statements.forEach(s -> s.desugar(resolveInfo));
  }

  @Contract(mutates = "param1")
  static void resolveWithoutDesugar(@NotNull SeqLike<Stmt> statements, @NotNull ResolveInfo resolveInfo, @NotNull ModuleLoader loader) {
    var shallowResolver = new StmtShallowResolver(loader, resolveInfo);
    shallowResolver.resolveStmt(statements, resolveInfo.thisModule());
    StmtResolver.resolveStmt(statements, resolveInfo);
    StmtResolver.resolveBind(statements, resolveInfo);
    var opSet = resolveInfo.opSet();
    opSet.reportIfCyclic();
  }

  default void desugar(@NotNull ResolveInfo resolveInfo) {
    new Desugarer(resolveInfo).accept(this);
  }

  @Override default @NotNull Doc toDoc(@NotNull DistillerOptions options) {
    return new ConcreteDistiller(options).stmt(this);
  }

  /**
   * @author re-xyr
   */
  enum Accessibility {
    Private("private"),
    Public("public");
    public final @NotNull String keyword;

    Accessibility(@NotNull String keyword) {
      this.keyword = keyword;
    }
  }
}
