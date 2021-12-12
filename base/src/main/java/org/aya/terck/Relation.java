// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.terck;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Relations between size of formal function parameter and function argument
 * in one recursive call. Together with the two operations {@link Relation#add(Relation)}
 * and {@link Relation#mul(Relation)} the relation set forms a commutative semi-ring
 * with zero {@link Relation#Unknown} and unit {@link Relation#Equal}.
 *
 * @author kiva
 */
public enum Relation {
  /** increase or unrelated of callee argument wrt. caller parameter. */
  Unknown("?"),
  /** structurally (maybe strictly) smaller than */
  Equal("="),
  /** structurally strictly smaller than */
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

  /**
   * A relation `A`is less than another relation `B` iff:
   * `A` contains less uncertainty than `B`, for example:
   * {@link Relation#Unknown} contains nothing about relations between
   * arguments and formal parameters while {@link Relation#LessThan}
   * declares the arguments is strictly structurally smaller than
   * formal parameters an unmovable fact.
   */
  public boolean lessThanOrEqual(@NotNull Relation rhs) {
    return this.ordinal() <= rhs.ordinal();
  }
}
