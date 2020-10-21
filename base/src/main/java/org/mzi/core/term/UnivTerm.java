package org.mzi.core.term;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.ref.LevelVar;
import org.mzi.util.Decision;

/**
 * @author ice1000
 */
public record UnivTerm() implements Term {
  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitUniv(this, p);
  }

  @Override public @NotNull Decision whnf() {
    return Decision.YES;
  }

  public record Level(@Nullable LevelVar var, int constant, int max) {
    public static final @NotNull Level INF = new Level(Integer.MAX_VALUE);

    @Contract(pure = true) public Level {
      assert max + constant >= -1 && (var == null || constant >= 0 && (var.kind() != LevelVar.Kind.U || max + constant >= 0));
      constant = var == null ? constant + max : constant;
      max = var == null ? 0 : max;
    }

    @Contract(pure = true) public Level(@NotNull LevelVar var, int constant) {
      this(var, constant, var.kind() == LevelVar.Kind.H ? -1 : 0);
      assert constant >= 0;
    }

    @Contract(pure = true) public Level(@NotNull LevelVar var) {
      this(var, 0);
    }

    @Contract(pure = true) public Level(int constant) {
      this(null, constant, 0);
    }

    @Contract(pure = true) public boolean closed() {
      return var == null;
    }

    @Contract(pure = true) public boolean isProp() {
      return closed() && constant == -1;
    }
  }
}
