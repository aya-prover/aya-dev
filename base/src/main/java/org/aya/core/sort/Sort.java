// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.sort;

import org.aya.api.error.SourcePos;
import org.aya.api.ref.LevelGenVar;
import org.aya.api.ref.Var;
import org.aya.generic.Level;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Not inspired from Arend.
 * <a href="https://github.com/JetBrains/Arend/blob/master/base/src/main/java/org/arend/core/sort/Sort.java"
 * >Sort.java</a>
 *
 * @author ice1000
 */
public record Sort(@NotNull Level<LvlVar> uLevel, @NotNull Level<LvlVar> hLevel) {
  public static final @NotNull Level<LvlVar> INF_LVL = new Level.Infinity<>();
  public static final @NotNull Sort OMEGA = new Sort(INF_LVL, INF_LVL);

  public static @Nullable SourcePos unsolvedPos(@NotNull Level<LvlVar> lvl) {
    return lvl instanceof Level.Reference<LvlVar> ref ? ref.ref().pos : null;
  }

  public @Nullable SourcePos unsolvedPos() {
    var pos = unsolvedPos(uLevel);
    return pos != null ? pos : unsolvedPos(hLevel);
  }

  public static @NotNull Level<LvlVar> constant(int value) {
    return new Level.Constant<>(value);
  }

  public @NotNull Sort subst(@NotNull LevelSubst subst) {
    var u = subst.applyTo(uLevel);
    var h = subst.applyTo(hLevel);
    return u == uLevel && h == hLevel ? this : new Sort(u, h);
  }

  public @NotNull Sort max(@NotNull Sort other) {
    return new Sort(max(uLevel, other.uLevel), max(hLevel, other.hLevel));
  }

  public static @NotNull Level<LvlVar> max(@NotNull Level<LvlVar> lhs, @NotNull Level<LvlVar> rhs) {
    if (lhs instanceof Level.Infinity || rhs instanceof Level.Infinity) return INF_LVL;
    if (lhs instanceof Level.Reference<LvlVar> l) {
      if (rhs instanceof Level.Reference<LvlVar> r) {
        if (l.ref() == r.ref()) return new Level.Reference<>(l.ref(), Math.max(l.lift(), r.lift()));
      } else if (rhs instanceof Level.Constant<LvlVar> r) {
        if (r.value() <= l.lift()) return l;
      }
    } else if (lhs instanceof Level.Constant<LvlVar> l) {
      if (rhs instanceof Level.Reference<LvlVar> r) {
        if (l.value() <= r.lift()) return r;
      } else if (rhs instanceof Level.Constant<LvlVar> r) {
        return new Level.Constant<>(Math.max(l.value(), r.value()));
      }
    }
    throw new UnsupportedOperationException("TODO: lmax");
  }

  @Contract("_-> new") public @NotNull Sort succ(int n) {
    return new Sort(uLevel.lift(n), hLevel.lift(n));
  }

  /**
   * @param pos <code>null</code> if this is a bound level var, otherwise it represents the place
   *            where it gets generated and the level needs to be solved.
   *            In well-typed terms it should always be <code>null</code>.
   * @author ice1000
   */
  public static final record LvlVar(
    @NotNull String name,
    @NotNull LevelGenVar.Kind kind,
    @Nullable SourcePos pos
  ) implements Var {
    @Override public boolean equals(@Nullable Object o) {
      return this == o;
    }

    @Override public int hashCode() {
      return System.identityHashCode(this);
    }

    public boolean free() {
      return pos != null;
    }
  }
}
