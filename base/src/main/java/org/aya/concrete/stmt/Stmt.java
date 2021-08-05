// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.stmt;

import kala.collection.immutable.ImmutableSeq;
import kala.tuple.Unit;
import org.aya.api.error.Reporter;
import org.aya.api.error.SourcePos;
import org.aya.concrete.desugar.BinOpSet;
import org.aya.concrete.desugar.Desugarer;
import org.aya.concrete.resolve.visitor.StmtResolver;
import org.aya.distill.ConcreteDistiller;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Docile;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * @author kiva
 */
public sealed interface Stmt extends Docile
  permits Decl, Sample, Generalize, Command {
  @Contract(pure = true) @NotNull SourcePos sourcePos();

  /** @apiNote the \import stmts do not have a meaningful accessibility, do not refer to this in those cases */
  @Contract(pure = true) @NotNull Accessibility accessibility();

  @Contract(mutates = "this")
  default void resolve(@NotNull BinOpSet opSet) {
    accept(StmtResolver.INSTANCE, opSet);
  }

  default void desugar(@NotNull Reporter reporter, @NotNull BinOpSet opSet) {
    accept(new Desugarer(reporter, opSet), Unit.unit());
  }

  @Override default @NotNull Doc toDoc() {
    return accept(ConcreteDistiller.INSTANCE, Unit.unit());
  }

  /**
   * @author re-xyr
   */
  interface Visitor<P, R> extends Decl.Visitor<P, R>, Generalize.Visitor<P, R>, Sample.Visitor<P, R> {
    default void visitAll(@NotNull ImmutableSeq<@NotNull Stmt> stmts, P p) {
      stmts.forEach(stmt -> stmt.accept(this, p));
      // [xyr]: Is this OK? The order of visiting must be preserved.
      // [ice]: I guess so, map should preserve the order.
    }
    R visitImport(@NotNull Command.Import cmd, P p);
    R visitOpen(@NotNull Command.Open cmd, P p);
    R visitModule(@NotNull Command.Module mod, P p);
    R visitBind(@NotNull Command.Bind bind, P p);
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
