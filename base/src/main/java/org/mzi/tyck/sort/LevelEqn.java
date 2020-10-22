package org.mzi.tyck.sort;

import asia.kala.collection.Seq;
import asia.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.ref.Ref;

import java.util.HashMap;
import java.util.Map;

public record LevelEqn<Var extends Ref>(
  @Nullable Var var1, @Nullable Var var2,
  int constant, int maxConstant
) {
  private static final int INVALID = -114514;

  public LevelEqn(@NotNull LevelEqn<? extends Var> copy) {
    this(copy.var1, copy.var2, copy.constant, copy.maxConstant);
  }

  @Contract(pure = true) @Override public @Nullable Var var1() {
    assert !isInf();
    return var1;
  }

  @Contract(pure = true) @Override public @Nullable Var var2() {
    assert !isInf();
    return var2;
  }

  @Contract(pure = true) public @Nullable Var var() {
    assert isInf();
    return var2;
  }

  @Contract(pure = true) public boolean isInf() {
    return constant == INVALID;
  }

  /**
   * A set of level equations.
   *
   * @param <Var> the level variable stored inside.
   */
  public record Set<Var extends Ref>(
    @NotNull Buffer<@NotNull Var> vars,
    @NotNull Buffer<@NotNull LevelEqn<Var>> eqns
  ) {
    public static final int INF = Integer.MAX_VALUE;

    public void add(@NotNull LevelEqn.Set<Var> other) {
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

    public @Nullable Seq<LevelEqn<Var>> solve(@NotNull Map<Var, Integer> solution) {
      Map<Var, Seq<LevelEqn<Var>>> paths = new HashMap<>();

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
            int a = solution.get(equation.var1());
            int b = solution.get(equation.var2());
            int m = equation.maxConstant;
            if (b != INF && (a == INF || (m == INVALID || a + m < 0) && b > a + equation.constant)) {
              if (a != INF) {
                var newPath = Buffer.from(paths.get(equation.var1()));
                newPath.append(equation);
                paths.put(equation.var2(), newPath);
              }
              if (i == 0 || equation.var2() == null && a != INF) {
                solution.remove(null);
                return paths.get(equation.var2());
              }

              solution.put(equation.var2(), a == INF ? INF : a + equation.constant);
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
