// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete;

import org.glavo.kala.Unit;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.error.SourcePos;
import org.mzi.concrete.desugar.Desugarer;
import org.mzi.concrete.pretty.StmtPrettyConsumer;
import org.mzi.concrete.resolve.visitor.StmtResolver;
import org.mzi.pretty.doc.Doc;

/**
 * @author kiva
 */
public sealed interface Stmt permits Decl, Stmt.ModuleStmt, Stmt.CmdStmt {
  @Contract(pure = true) @NotNull SourcePos sourcePos();

  /** @apiNote the \import and \module stmts do not have a meaningful accessibility, do not refer to this in those cases */
  @Contract(pure = true) @NotNull Accessibility accessibility();

  default void resolve() {
    accept(StmtResolver.INSTANCE, Unit.unit());
  }

  default void desugar() {
    accept(Desugarer.INSTANCE, Unit.unit());
  }

  default @NotNull Doc toDoc() {
    return accept(StmtPrettyConsumer.INSTANCE, Unit.unit());
  }

  /**
   * @author re-xyr
   */
  interface Visitor<P, R> extends Decl.Visitor<P, R> {
    default @NotNull ImmutableSeq<R> visitAll(@NotNull ImmutableSeq<@NotNull Stmt> stmts, P p) {
      return stmts.map(stmt -> stmt.accept(this, p));
      // [xyr]: Is this OK? The order of visiting must be preserved.
      // [ice]: I guess so, map should preserve the order.
    }
    R visitCmd(@NotNull CmdStmt cmd, P p);
    R visitModule(@NotNull ModuleStmt mod, P p);
  }

  <P, R> R accept(@NotNull Visitor<P, R> visitor, P p);

  /**
   * @author re-xyr
   */
  enum Accessibility {
    Private,
    Public;

    public boolean lessThan(Accessibility accessibility) {
      return ordinal() < accessibility.ordinal();
    }
  }

  /**
   * @author re-xyr
   */
  record ModuleStmt(
    @NotNull SourcePos sourcePos,
    @NotNull String name,
    @NotNull ImmutableSeq<@NotNull Stmt> contents
  ) implements Stmt {

    @Override
    public @NotNull Accessibility accessibility() {
      return Accessibility.Public;
    }

    @Override
    public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitModule(this, p);
    }
  }

  record CmdStmt(
    @NotNull SourcePos sourcePos,
    @NotNull Accessibility accessibility,
    @NotNull Cmd cmd,
    @NotNull ImmutableSeq<@NotNull String> path,
    @NotNull UseHide useHide
  ) implements Stmt {
    @Override
    public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitCmd(this, p);
    }

    /**
     * @author kiva
     */
    public enum Cmd {
      Open,
      Import,
    }

    /**
     * @author re-xyr
     */
    public record UseHide(@NotNull ImmutableSeq<@NotNull String> list, @NotNull Strategy strategy) {
      public static final UseHide EMPTY = new Stmt.CmdStmt.UseHide(ImmutableSeq.empty(), Stmt.CmdStmt.UseHide.Strategy.Hiding);

      public boolean uses(String name) {
        return switch (strategy) {
          case Using -> list.contains(name);
          case Hiding -> !list.contains(name);
        };
      }

      /**
       * @author re-xyr
       */
      public enum Strategy {
        Using,
        Hiding,
      }
    }
  }
}
