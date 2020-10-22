package org.mzi.tyck.sort;

import asia.kala.collection.Seq;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.ref.Ref;
import org.mzi.core.subst.LevelSubst;
import org.mzi.ref.LevelVar;

/**
 * Highly inspired from Arend.
 * <a href="https://github.com/JetBrains/Arend/blob/master/base/src/main/java/org/arend/core/sort/Sort.java"
 * >Sort.java</a>
 */
public record Sort(@NotNull Level uLevel, @NotNull Level hLevel) implements LevelSubst {
  // TODO[JDK-8247334]: uncomment when we move to JDK16
  public static final /*@NotNull*/ Sort PROP = new Sort(0, -1);
  public static final /*@NotNull*/ Sort SET0 = (hSet(new Level(0)));
  public static final /*@NotNull*/ Sort STD = (new Sort(new Level(LevelVar.UP), new Level(LevelVar.HP)));

  public static @NotNull Sort hSet(@NotNull Level uLevel) {
    return new Sort(uLevel, new Level(0));
  }

  public static @NotNull Sort hType(@NotNull Level uLevel) {
    return new Sort(uLevel, Level.INF);
  }

  public Sort(int uLevel, int hLevel) {
    this(new Level(uLevel), new Level(hLevel));
  }

  @Contract(pure = true) public boolean isOmega() {
    return uLevel.isInf();
  }

  public @NotNull Sort succ() {
    return isProp() ? SET0 : new Sort(uLevel.plus(1), hLevel.plus(1));
  }

  public @NotNull Sort max(@NotNull Sort sort) {
    if (isProp()) return sort;
    if (sort.isProp()) return this;
    if (uLevel.var != null && sort.uLevel.var != null && uLevel.var != sort.uLevel.var ||
      hLevel.var != null && sort.hLevel.var != null && hLevel.var != sort.hLevel.var) {
      throw new UnsupportedOperationException();
    } else return new Sort(uLevel.max(sort.uLevel), hLevel.max(sort.hLevel));
  }

  @Contract(pure = true) public boolean isProp() {
    return hLevel.isProp();
  }

  @Contract(pure = true) public boolean isSet() {
    return hLevel.closed() && hLevel.constant == 0;
  }

  public boolean isStd() {
    return uLevel.varOnly() && uLevel.var == LevelVar.UP && hLevel.varOnly() && hLevel.var == LevelVar.HP;
  }

  @Override public boolean isEmpty() {
    return uLevel.var() == LevelVar.UP && uLevel.varOnly() && hLevel.var() == LevelVar.HP && hLevel.varOnly();
  }

  @Override public Level get(@NotNull Ref ref) {
    return ref == LevelVar.UP ? uLevel : ref == LevelVar.HP ? hLevel : null;
  }

  @Override
  public @NotNull LevelSubst subst(@NotNull LevelSubst substitution) {
    return new Sort(uLevel.subst(substitution), hLevel.subst(substitution));
  }

  public @NotNull Sort substSort(@NotNull LevelSubst subst) {
    return subst.isEmpty() || uLevel.closed() && hLevel.closed() ? this : new Sort(uLevel.subst(subst), hLevel.subst(subst));
  }

  /**
   * A level is represented as max({@link Level#var} + {@link Level#constant}, {@link Level#max}).
   *
   */
  public record Level(@Nullable LevelVar var, int constant, int max) {
    // TODO[JDK-8247334]: uncomment when we move to JDK16
    public static final /*@NotNull*/ Level INF = new Level(Integer.MAX_VALUE);

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

    @Contract(pure = true) public boolean isInf() {
      return this == INF;
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

    @Contract(pure = true) public @NotNull Level plus(int constant) {
      return constant == 0 || isInf() ? this : new Level(var, this.constant + constant, max);
    }

    public @NotNull Level max(@NotNull Level level) {
      if (Seq.of(this, level).contains(INF)) return INF;

      if (var != null && level.var != null) {
        if (var == level.var) {
          int newConstant = Math.max(constant, level.constant);
          return new Level(var, newConstant,
            Math.max(constant + max, level.constant + level.max) - newConstant);
        } else throw new UnsupportedOperationException();
        // ^ multiple variables, take the maximum
      }
      if (var == null && level.var == null) return new Level(Math.max(constant, level.constant));

      int newConstant = var == null ? constant : level.constant;
      Level lvl = var == null ? level : this;
      return newConstant <= lvl.maxAddConstant() ? lvl :
        new Level(lvl.var, lvl.constant,
          Math.max(lvl.max, newConstant - lvl.constant));
    }

    public Level subst(@NotNull LevelSubst subst) {
      if (var == null || isInf()) return this;
      var level = subst.get(var);
      if (level == null) return this;
      if (level.isInf() || varOnly()) return level;
      if (level.var != null)
        return new Level(level.var, level.constant + constant,
          Math.max(level.max, max - level.constant));
      else return new Level(Math.max(level.constant, max) + constant);
    }

    /*
    public static boolean compare(Level level1, Level level2, CMP cmp, Equations equations, Expr expr) {
      if (cmp == CMP.GE) {
        return compare(level2, level1, CMP.LE, equations, expr);
      }

      if (level1.isInf())
        return level2.isInf() || !level2.closed() &&
          (equations == null || equations.addEquation(INF, level2, CMP.LE, expr));
      if (level2.isInf())
        return cmp == CMP.LE || !level1.closed() &&
          (equations == null || equations.addEquation(INF, level1, CMP.LE, expr));

      if (level2.var() == null && level1.var() != null && level1.var().hole() == null)
        return false;

      if (level1.var() == null && cmp == CMP.LE) {
        if (level1.constant <= level2.constant + level2.max) return true;
      }

      if (level1.var() == level2.var()) {
        if (cmp == CMP.LE)
          return level1.constant <= level2.constant
            && level1.maxAddConstant() <= level2.maxAddConstant();
        else return level1.constant == level2.constant && level1.max == level2.max;
      } else {
        if (equations == null)
          return level1.var() != null && level1.var().hole() != null || level2.var().hole() != null;
        else return equations.addEquation(level1, level2, cmp, expr);
      }
    }
    */
  }
}
