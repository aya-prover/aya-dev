package org.mzi.core.term;

import asia.kala.collection.Seq;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.core.subst.LevelSubst;
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

    @Contract(pure = true) public boolean withMaxConstant() {
      return var != null && (max > 0 || var.kind() == LevelVar.Kind.H && max == 0);
    }

    @Contract(pure = true) public boolean varOnly() {
      return var != null && constant == 0 && !withMaxConstant();
    }

    @Contract(pure = true) public int maxAddConstant() {
      return constant + max;
    }

    @Contract(pure = true) public @NotNull Level add(int constant) {
      return constant == 0 || this == INF ? this : new Level(var, this.constant + constant, max);
    }

    public @Nullable Level max(@NotNull Level level) {
      if (Seq.of(this, level).contains(INF)) return INF;

      if (var != null && level.var != null) {
        if (var == level.var) {
          int newConstant = Math.max(constant, level.constant);
          return new Level(var, newConstant,
            Math.max(constant + max, level.constant + level.max) - newConstant);
        } else return null;
      }
      if (var == null && level.var == null) return new Level(Math.max(constant, level.constant));

      int newConstant = var == null ? constant : level.constant;
      Level lvl = var == null ? level : this;
      return newConstant <= lvl.maxAddConstant() ? lvl :
        new Level(lvl.var, lvl.constant,
          Math.max(lvl.max, newConstant - lvl.constant));
    }

    public Level subst(@NotNull LevelSubst subst) {
      if (var == null || this == INF) return this;
      var level = subst.get(var);
      if (level == null) return this;
      if (level == INF || varOnly()) return level;
      if (level.var != null)
        return new Level(level.var, level.constant + constant,
          Math.max(level.max, max - level.constant));
      else return new Level(Math.max(level.constant, max) + constant);
    }

    /*
    public static boolean compare(Level level1, Level level2, CMP cmp, Equations equations, Concrete.SourceNode sourceNode) {
      if (cmp == CMP.GE) {
        return compare(level2, level1, CMP.LE, equations, sourceNode);
      }

      if (level1 == INF) {
        return level2 == INF || !level2.closed() && (equations == null || equations.addEquation(INFINITY, level2, CMP.LE, sourceNode));
      }
      if (level2 == INF) {
        return cmp == CMP.LE || !level1.closed() && (equations == null || equations.addEquation(INFINITY, level1, CMP.LE, sourceNode));
      }

      if (level2.var() == null && level1.var() != null && !(level1.var() instanceof InferenceLevelVariable))
        return false;

      if (level1.var() == null && cmp == CMP.LE) {
        if (level1.constant <= level2.constant + level2.max) {
          return true;
        }
      }

      if (level1.var() == level2.var()) {
        if (cmp == CMP.LE) {
          return level1.constant <= level2.constant && level1.getMaxAddedConstant() <= level2.getMaxAddedConstant();
        } else {
          return level1.constant == level2.constant && level1.getMaxConstant() == level2.getMaxConstant();
        }
      } else {
        if (equations == null) {
          return level1.var() instanceof InferenceLevelVariable || level2.var() instanceof InferenceLevelVariable;
        } else {
          return equations.addEquation(level1, level2, cmp, sourceNode);
        }
      }
    }
    */
  }
}
