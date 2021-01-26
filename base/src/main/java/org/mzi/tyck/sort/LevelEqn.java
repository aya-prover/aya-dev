// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.tyck.sort;

import org.glavo.kala.collection.Seq;
import org.glavo.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.error.Reporter;
import org.mzi.api.ref.Var;
import org.mzi.concrete.Expr;
import org.mzi.ref.LevelVar;
import org.mzi.util.Ordering;

import java.util.Map;

public class LevelEqn<V extends Var> {
  /**
   * A set of level equations.
   */
  public record Set(
    @NotNull Reporter reporter,
    @NotNull Buffer<@NotNull LevelVar> vars,
    @NotNull Buffer<@NotNull LevelEqn<LevelVar>> eqns
  ) {
    public boolean add(Sort.@NotNull Level level1, @NotNull Sort.Level level2, @NotNull Ordering cmp, Expr expr) {
      throw new UnsupportedOperationException("#93");
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
      throw new UnsupportedOperationException("#93");
    }
  }
}
