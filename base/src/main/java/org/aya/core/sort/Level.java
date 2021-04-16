// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.sort;

import org.aya.api.ref.LevelVar;
import org.aya.util.Constants;
import org.glavo.kala.value.Ref;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 * @see LevelVar
 * @see org.aya.concrete.Expr.UnivExpr
 */
public sealed interface Level {
  @SuppressWarnings("unchecked") static LevelVar<Level> narrow(LevelVar<?> levelVar) {
    return (LevelVar<Level>) levelVar;
  }
  @NotNull Level succ();
  default @NotNull Level subst(@NotNull LevelSubst subst) {
    return this;
  }

  /**
   * Unlike {@link Reference}, this one is the implicit polymorphic level.
   * It is related to the underlying definition.
   */
  record Polymorphic(int lift) implements Level {
    public static @NotNull LevelVar<Level> make(int lift, LevelVar.Kind kind) {
      return new LevelVar<>(Constants.ANONYMOUS_PREFIX, kind, new Ref<>(new Polymorphic(lift)));
    }

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
    public static @NotNull LevelVar<Level> make(int level, LevelVar.Kind kind) {
      return new LevelVar<>(Constants.ANONYMOUS_PREFIX, kind, new Ref<>(new Constant(level)));
    }

    @Override public @NotNull Level succ() {
      return new Constant(value + 1);
    }
  }

  record Reference(@NotNull LevelVar<Level> ref, int lift) implements Level {
    @Override public @NotNull Level succ() {
      return new Reference(ref, lift + 1);
    }

    @Override public @NotNull Level subst(@NotNull LevelSubst subst) {
      return subst.get(ref).getOrDefault(this);
    }
  }
}
