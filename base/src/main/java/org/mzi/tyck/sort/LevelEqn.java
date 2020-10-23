package org.mzi.tyck.sort;

import asia.kala.collection.Seq;
import asia.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.ref.Var;

import java.util.HashMap;
import java.util.Map;

public record LevelEqn<V extends Var>(
  @Nullable V v1, @Nullable V v2,
  int constant, int max
) {
  private static final int INVALID = -114514;

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
   *
   * @param <V> the level variable stored inside.
   */
  public record Set<V extends Var>(
    @NotNull Buffer<@NotNull V> vars,
    @NotNull Buffer<@NotNull LevelEqn<V>> eqns
  ) {
    public static final int INF = Integer.MAX_VALUE;

    public void add(@NotNull LevelEqn.Set<V> other) {
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

    public @Nullable Seq<LevelEqn<V>> solve(@NotNull Map<V, Integer> solution) {
      Map<V, Seq<LevelEqn<V>>> paths = new HashMap<>();

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
