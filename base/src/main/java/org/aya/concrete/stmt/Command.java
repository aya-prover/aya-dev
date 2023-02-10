// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.stmt;

import kala.collection.immutable.ImmutableSeq;
import org.aya.resolve.context.ModulePath;
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
    @NotNull ModulePath.Qualified path,
    @Nullable String asName,
    @NotNull Accessibility accessibility
  ) implements Command {
  }

  /**
   * @author re-xyr
   */
  record Open(
    @Override @NotNull SourcePos sourcePos,
    @NotNull Accessibility accessibility,
    @NotNull ModulePath.Qualified path,
    @NotNull UseHide useHide,
    boolean openExample,
    boolean fromSugar
  ) implements Command {
  }

  /**
   * @author re-xyr
   */
  record Module(
    @Override @NotNull SourcePos sourcePos,
    @NotNull SourcePos entireSourcePos,
    @NotNull String name,
    @NotNull ImmutableSeq<@NotNull Stmt> contents
  ) implements Command {

    @Override public @NotNull Accessibility accessibility() {
      return Accessibility.Public;
    }
  }
}
