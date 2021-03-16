// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.sort;

import org.aya.api.ref.Var;
import org.aya.concrete.Expr;
import org.aya.ref.LevelVar;
import org.aya.util.Ordering;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Highly inspired from Arend.
 * <a href="https://github.com/JetBrains/Arend/blob/master/base/src/main/java/org/arend/core/sort/Sort.java"
 * >Sort.java</a>
 */
public record Sort(@NotNull Level uLevel, @NotNull Level hLevel) implements LevelSubst {
  // TODO[level]
  public static final @NotNull Sort OMEGA = new Sort(new Level(), new Level());

  @Override public boolean isEmpty() {
    throw new UnsupportedOperationException("#93");
  }

  @Contract(pure = true) @Override public @Nullable Level get(@NotNull Var var) {
    return var == LevelVar.UP ? uLevel : var == LevelVar.HP ? hLevel : null;
  }

  @Contract("_ -> new") @Override
  public @NotNull LevelSubst subst(@NotNull LevelSubst substitution) {
    return new Sort(uLevel.subst(substitution), hLevel.subst(substitution));
  }

  public @NotNull Sort substSort(@NotNull LevelSubst subst) {
    // TODO[level]
    return this;
  }

  public static boolean compare(
    @NotNull Sort sort1, @NotNull Sort sort2, @NotNull Ordering cmp,
    @NotNull LevelEqn.Set equations, Expr expr
  ) {
    // TODO[level]
    return true;
  }

  public static class Level {
    public Level subst(@NotNull LevelSubst subst) {
      throw new UnsupportedOperationException("#93");
    }

    @Override public String toString() {
      return "Level";
    }

    public static boolean compare(
      @NotNull Level level1, @NotNull Level level2, @NotNull Ordering cmp,
      @Nullable LevelEqn.Set equations, Expr expr
    ) {
      throw new UnsupportedOperationException("#93");
    }
  }
}
