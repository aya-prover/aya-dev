// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.ref;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.ref.Var;
import org.mzi.concrete.Expr;

public record LevelVar(
  @NotNull String name,
  @NotNull Kind kind,
  @Nullable LevelHole hole
) implements Var {
  public LevelVar(@NotNull String name, @NotNull Kind kind) {
    this(name, kind, null);
  }

  /**
   * Information about a level expression in the concrete syntax
   */
  public record LevelHole(@NotNull Expr expr, boolean isUniv) {
  }

  public enum Kind {
    U(UP), H(HP);

    public final @NotNull LevelVar std;
    @Contract(pure = true) Kind(@NotNull LevelVar std) {
      this.std = std;
    }
  }

  // TODO[JDK-8247334]: uncomment when we move to JDK16
  public static final /*@NotNull*/ LevelVar UP = new LevelVar("ul", Kind.U);
  public static final /*@NotNull*/ LevelVar HP = new LevelVar("hl", Kind.H);
}
