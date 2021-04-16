// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.sort;

import org.aya.concrete.Expr;
import org.aya.util.Ordering;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Highly inspired from Arend.
 * <a href="https://github.com/JetBrains/Arend/blob/master/base/src/main/java/org/arend/core/sort/Sort.java"
 * >Sort.java</a>
 *
 * @author ice1000
 */
public record Sort(@NotNull Level uLevel, @NotNull Level hLevel) {
  public static final @NotNull Sort OMEGA = new Sort(Level.Infinity.INSTANCE, Level.Infinity.INSTANCE);

  public @NotNull Sort substSort(@NotNull LevelSubst subst) {
    return new Sort(uLevel.subst(subst), hLevel.subst(subst));
  }

  public static boolean compare(
    @NotNull Sort sort1, @NotNull Sort sort2, @NotNull Ordering cmp,
    @NotNull LevelEqn.Set equations, Expr expr
  ) {
    // TODO[level]
    return true;
  }

  @Contract(" -> new") public @NotNull Sort succ() {
    return new Sort(uLevel.succ(), hLevel.succ());
  }
}
