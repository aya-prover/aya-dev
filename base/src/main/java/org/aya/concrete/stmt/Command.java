// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.stmt;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public sealed interface Command extends Stmt {
  /**
   * @author re-xyr
   */
  record Import(
    @Override @NotNull SourcePos sourcePos,
    @NotNull QualifiedID path,
    @Nullable String asName
  ) implements Command {

    @Override public @NotNull Accessibility accessibility() {
      return Accessibility.Private;
    }

    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitImport(this, p);
    }
  }

  /**
   * @author re-xyr
   */
  record Open(
    @Override @NotNull SourcePos sourcePos,
    @NotNull Accessibility accessibility,
    @NotNull QualifiedID path,
    @NotNull UseHide useHide
  ) implements Command {
    public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitOpen(this, p);
    }

    /**
     * @author re-xyr
     */
    public record UseHide(@NotNull ImmutableSeq<@NotNull String> list, @NotNull UseHide.Strategy strategy) {
      public static final UseHide EMPTY = new UseHide(ImmutableSeq.empty(), UseHide.Strategy.Hiding);

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

  /**
   * @author re-xyr
   */
  record Module(
    @Override @NotNull SourcePos sourcePos,
    @NotNull String name,
    @NotNull ImmutableSeq<@NotNull Stmt> contents
  ) implements Command {

    @Override public @NotNull Accessibility accessibility() {
      return Accessibility.Public;
    }

    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitModule(this, p);
    }
  }
}
