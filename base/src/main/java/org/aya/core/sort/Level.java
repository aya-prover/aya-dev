// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.sort;

import org.aya.concrete.LevelPrevar;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 * @see LevelPrevar
 * @see org.aya.concrete.Expr.UnivExpr
 */
public sealed interface Level {
  @NotNull Level succ();
  default @NotNull Level subst(@NotNull LevelSubst subst) {
    return this;
  }

  /**
   * Unlike {@link Reference}, this one is the implicit polymorphic level.
   * It is related to the underlying definition.
   */
  record Polymorphic(int lift) implements Level {
    @Override public @NotNull Level succ() {
      return new Polymorphic(lift + 1);
    }
  }

  final class Infinity implements Level {
    public static final @NotNull Infinity INSTANCE = new Infinity();

    private Infinity() {
    }

    @Override public @NotNull Level succ() {
      return this;
    }
  }

  record Constant(int value) implements Level {
    @Override public @NotNull Level succ() {
      return new Constant(value + 1);
    }
  }

  record Reference(@NotNull LevelVar ref, int lift) implements Level {
    public Reference(@NotNull LevelVar ref) {
      this(ref, 0);
    }

    @Override public @NotNull Level succ() {
      return new Reference(ref, lift + 1);
    }

    @Override public @NotNull Level subst(@NotNull LevelSubst subst) {
      return subst.get(ref).getOrDefault(this);
    }
  }
}
