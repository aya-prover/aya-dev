// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.sort;

import org.aya.api.ref.Var;
import org.aya.concrete.Expr;
import org.aya.concrete.LevelPrevar;
import org.aya.util.Ordering;
import org.glavo.kala.collection.Seq;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.collection.mutable.MutableMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class LevelEqn {
  /**
   * A set of level equations.
   */
  public record Set(
    @NotNull MutableMap<LevelPrevar, LevelVar> vars,
    @NotNull Buffer<@NotNull LevelEqn> eqns
  ) {
    public boolean add(@NotNull Level level1, @NotNull Level level2, @NotNull Ordering cmp, Expr expr) {
      throw new UnsupportedOperationException("#93");
    }

    public void add(@NotNull LevelEqn.Set other) {
      vars.putAll(other.vars);
      eqns.appendAll(other.eqns);
    }

    public void clear() {
      vars.clear();
      eqns.clear();
    }

    public boolean isEmpty() {
      return vars.isEmpty() && eqns.isEmpty();
    }

    public @Nullable Seq<LevelEqn> solve(@NotNull Map<Var, Integer> solution) {
      throw new UnsupportedOperationException("#93");
    }
  }
}
