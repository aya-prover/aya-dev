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
  /**
   * Unlike {@link Reference}, this one is the implicit polymorphic level.
   * It is related to the underlying definition.
   */
  final class Polymorphic implements Level {
    public static final @NotNull Level.Polymorphic INSTANCE = new Polymorphic();
    public static final @NotNull LevelVar<Level> H_VAR = new LevelVar<>(Constants.ANONYMOUS_PREFIX, LevelVar.Kind.Homotopy, new Ref<>(INSTANCE));
    public static final @NotNull LevelVar<Level> U_VAR = new LevelVar<>(Constants.ANONYMOUS_PREFIX, LevelVar.Kind.Universe, new Ref<>(INSTANCE));

    private Polymorphic() {
    }
  }

  record Constant(int value) implements Level {
  }

  record Reference(@NotNull LevelVar<Level> ref, int lift) implements Level {
  }
}
