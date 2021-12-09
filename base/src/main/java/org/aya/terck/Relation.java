// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.terck;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public enum Relation {
  Unknown("?"),
  Equal("="),
  LessThan("<");

  private final @NotNull String text;

  @Contract(pure = true) Relation(@NotNull String text) {
    this.text = text;
  }

  @Override public String toString() {
    return text;
  }

  @Contract(pure = true) public @NotNull Relation mul(@NotNull Relation rhs) {
    return switch (this) {
      case Unknown -> Unknown;
      case Equal -> rhs;
      case LessThan -> rhs == Unknown ? Unknown : LessThan;
    };
  }

  @Contract(pure = true) public @NotNull Relation add(@NotNull Relation rhs) {
    return this.lessThanOrEqual(rhs) ? rhs : this;
  }

  public boolean lessThanOrEqual(@NotNull Relation rhs) {
    return this.ordinal() <= rhs.ordinal();
  }
}
