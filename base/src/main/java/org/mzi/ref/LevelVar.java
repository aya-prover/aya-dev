package org.mzi.ref;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.ref.Ref;
import org.mzi.concrete.term.Expr;

public record LevelVar(
  @NotNull String name,
  @NotNull Kind kind,
  @Nullable LevelHole hole
) implements Ref {
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
  public static @NotNull LevelVar UP = new LevelVar("ul", Kind.U);
  public static @NotNull LevelVar HP = new LevelVar("hl", Kind.H);
}
