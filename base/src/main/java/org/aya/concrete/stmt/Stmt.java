// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.stmt;

import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableSeq;
import kala.tuple.Unit;
import org.aya.concrete.desugar.Desugarer;
import org.aya.concrete.remark.Remark;
import org.aya.distill.ConcreteDistiller;
import org.aya.pretty.doc.Doc;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.module.ModuleLoader;
import org.aya.resolve.visitor.StmtResolver;
import org.aya.resolve.visitor.StmtShallowResolver;
import org.aya.tyck.order.TyckUnit;
import org.aya.util.distill.AyaDocile;
import org.aya.util.distill.DistillerOptions;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * @author kiva
 */
public sealed interface Stmt extends AyaDocile, TyckUnit
  permits Remark, ClassDecl, Command, Decl, Generalize {
  /** @apiNote the \import stmts do not have a meaningful accessibility, do not refer to this in those cases */
  @Contract(pure = true) @NotNull Accessibility accessibility();

  @Contract(mutates = "param1")
  static void resolve(@NotNull SeqLike<Stmt> statements, @NotNull ResolveInfo resolveInfo, @NotNull ModuleLoader loader) {
    var shallowResolver = new StmtShallowResolver(loader, resolveInfo);
    shallowResolver.resolveStmt(statements, resolveInfo.thisModule());
    StmtResolver.resolveStmt(statements, resolveInfo);
    StmtResolver.resolveBind(statements, resolveInfo);
    var opSet = resolveInfo.opSet();
    opSet.reportIfCyclic();
    statements.forEach(s -> s.desugar(resolveInfo));
  }

  default void desugar(@NotNull ResolveInfo resolveInfo) {
    accept(new Desugarer(resolveInfo), Unit.unit());
  }

  @Override default @NotNull Doc toDoc(@NotNull DistillerOptions options) {
    return new ConcreteDistiller(options).stmt(this);
  }

  /**
   * @author re-xyr
   */
  interface Visitor<P, R> extends Decl.Visitor<P, R>, ClassDecl.Visitor<P, R> {
    default void visitAll(@NotNull ImmutableSeq<@NotNull Stmt> stmts, P p) {
      stmts.forEach(stmt -> stmt.accept(this, p));
      // [xyr]: Is this OK? The order of visiting must be preserved.
      // [ice]: I guess so, map should preserve the order.
    }
    R visitImport(@NotNull Command.Import cmd, P p);
    R visitOpen(@NotNull Command.Open cmd, P p);
    R visitModule(@NotNull Command.Module mod, P p);
    R visitRemark(@NotNull Remark remark, P p);
    R visitGeneralize(@NotNull Generalize generalize, P p);
  }

  <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p);

  default <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return doAccept(visitor, p);
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

    public boolean lessThan(Accessibility accessibility) {
      return ordinal() < accessibility.ordinal();
    }
  }

}
