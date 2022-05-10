// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.stmt;

import kala.collection.Map;
import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.tuple.Tuple;
import org.aya.util.binop.Assoc;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public sealed interface Command extends Stmt {
  @Override default boolean needTyck(@NotNull ImmutableSeq<String> currentMod) {
    // commands are desugared in the shallow resolver
    return false;
  }

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
  }

  /**
   * @author re-xyr
   */
  record Open(
    @Override @NotNull SourcePos sourcePos,
    @NotNull Accessibility accessibility,
    @NotNull QualifiedID path,
    @NotNull UseHide useHide,
    boolean openExample
  ) implements Command {
    /**
     * @author re-xyr
     */
    public record UseHide(@NotNull ImmutableSeq<@NotNull UseHideName> list, @NotNull UseHide.Strategy strategy) {
      public static final UseHide EMPTY = new UseHide(ImmutableSeq.empty(), UseHide.Strategy.Hiding);

      public boolean uses(@NotNull String name) {
        return switch (strategy) {
          case Using -> list.find(n -> n.id.equals(name)).isDefined();
          case Hiding -> list.find(n -> n.id.equals(name)).isEmpty();
        };
      }

      public @NotNull Map<String, String> renaming() {
        if (strategy == UseHide.Strategy.Hiding) return ImmutableMap.empty();
        return list.view().map(i -> Tuple.of(i.id, i.asName)).toImmutableMap();
      }

      /**
       * @author re-xyr
       */
      public enum Strategy {
        Using,
        Hiding,
      }
    }

    public record UseHideName(
      @NotNull String id,
      @NotNull String asName,
      @NotNull Assoc asAssoc,
      @NotNull BindBlock asBind
    ) {
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
  }
}
