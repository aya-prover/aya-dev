package org.mzi.ref;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.ref.Ref;

public record LevelVar(
  @NotNull String name,
  @NotNull Kind kind
) implements Ref {
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
