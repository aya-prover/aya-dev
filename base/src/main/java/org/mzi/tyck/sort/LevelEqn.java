package org.mzi.tyck.sort;

import asia.kala.collection.Seq;
import asia.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.ref.Var;
import org.mzi.concrete.Expr;
import org.mzi.ref.LevelVar;
import org.mzi.util.Ordering;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public record LevelEqn<V extends Var>(
  @Nullable V v1, @Nullable V v2,
  int constant, int max
) {
  private static final int INVALID = -114514;

  public LevelEqn(@NotNull V v) {
    this(null, v, INVALID, INVALID);
  }

  public LevelEqn(@NotNull LevelEqn<? extends V> copy) {
    this(copy.v1, copy.v2, copy.constant, copy.max);
  }

  @Contract(pure = true) public @Nullable V v1() {
    assert !isInf();
    return v1;
  }

  @Contract(pure = true) public @Nullable V v2() {
    assert !isInf();
    return v2;
  }

  @Contract(pure = true) public @Nullable V var() {
    assert isInf();
    return v2;
  }

  @Contract(pure = true) public boolean isInf() {
    return constant == INVALID;
  }

  /**
   * A set of level equations.
   */
  public record Set(
    @NotNull Buffer<@NotNull LevelVar> vars,
    @NotNull Buffer<@NotNull LevelEqn<LevelVar>> eqns
  ) {
    public static final int INF = Integer.MAX_VALUE;

    private void addLevelEquation(@Nullable LevelVar var, Expr expr) {
      if (hasHole(var)) eqns.append(new LevelEqn<>(var));
      // TODO[ice]: report an error otherwise
    }

    @Contract(value = "null -> false", pure = true) private boolean hasHole(@Nullable LevelVar var) {
      return var != null && var.hole() != null;
    }

    private void addLevelEquation(LevelVar var1, LevelVar var2, int constant, int maxConstant, Expr expr) {
      // _ <= max(-c, -d), _ <= max(l - c, -d) // 6
      if (!hasHole(var2) && maxConstant < 0 && (constant < 0 || constant == 0 && var2 == LevelVar.HP && var1 == null) &&
        !(var2 == null && hasHole(var1) && var1.kind() == LevelVar.Kind.H && constant >= -1 && maxConstant >= -1)) {
        // TODO[ice]: report error
        return;
      }

      // l <= max(l - c, +d), l <= max(+-c, +-d) // 4
      if ((var1 == LevelVar.UP || var1 == LevelVar.HP) && !hasHole(var2) && (var2 == null || constant < 0)) {
        // TODO[ice]: report error
        return;
      }

      eqns.append(new LevelEqn<>(var1, var2, constant, maxConstant));
    }

    public boolean add(Sort.@NotNull Level level1, @NotNull Sort.Level level2, @NotNull Ordering cmp, Expr expr) {
      if (level1.isInf() && level2.isInf() || level1.isInf() && cmp == Ordering.Gt || level2.isInf() && cmp == Ordering.Lt)
        return true;
      if (level1.isInf()) {
        addLevelEquation(level2.var(), expr);
        return true;
      }
      if (level2.isInf()) {
        addLevelEquation(level1.var(), expr);
        return true;
      }

      if (cmp == Ordering.Lt || cmp == Ordering.Eq) {
        addLevelEquation(level1.var(), level2.var(), level2.constant() - level1.constant(), level2.maxAddConstant() - level1.constant(), expr);
        if (level1.withMaxConstant() && level1.maxAddConstant() > level2.maxAddConstant()) {
          addLevelEquation(null, level2.var(), level2.constant() - level1.maxAddConstant(), -1, expr);
        }
        // NOTE[ice]: Some code is commented here in Arend
      }
      if (cmp == Ordering.Gt || cmp == Ordering.Eq) {
        addLevelEquation(level2.var(), level1.var(), level1.constant() - level2.constant(), level1.maxAddConstant() - level2.constant(), expr);
        if (level2.withMaxConstant() && level2.maxAddConstant() > level1.maxAddConstant()) {
          addLevelEquation(null, level1.var(), level1.constant() - level2.maxAddConstant(), -1, expr);
        }
        // NOTE[ice]: Some code is commented here in Arend
      }
      return true;
    }

    public void add(@NotNull LevelEqn.Set other) {
      vars.appendAll(other.vars);
      eqns.appendAll(other.eqns);
    }

    public void clear() {
      vars.clear();
      eqns.clear();
    }

    public boolean isEmpty() {
      return vars.isEmpty() && eqns.isEmpty();
    }

    public @Nullable Seq<LevelEqn<LevelVar>> solve(@NotNull Map<Var, Integer> solution) {
      Map<Var, Seq<LevelEqn<LevelVar>>> paths = new HashMap<>();

      solution.put(null, 0);
      paths.put(null, Buffer.of());
      for (var var : vars) {
        solution.put(var, 0);
        paths.put(var, Buffer.of());
      }

      for (int i = vars.size(); i >= 0; i--) {
        boolean updated = false;
        for (var equation : eqns) {
          if (equation.isInf()) {
            var prev = solution.put(equation.var(), INF);
            if (prev == null || prev != INF) updated = true;
          } else {
            int a = solution.get(equation.v1());
            int b = solution.get(equation.v2());
            int m = equation.max;
            if (b != INF && (a == INF || (m == INVALID || a + m < 0) && b > a + equation.constant)) {
              if (a != INF) {
                var newPath = Buffer.from(paths.get(equation.v1()));
                newPath.append(equation);
                paths.put(equation.v2(), newPath);
              }
              if (i == 0 || equation.v2() == null && a != INF) {
                solution.remove(null);
                return paths.get(equation.v2());
              }

              solution.put(equation.v2(), a == INF ? INF : a + equation.constant);
              updated = true;
            }
          }
        }
        if (!updated) break;
      }

      solution.remove(null);
      return null;
    }
  }
}
