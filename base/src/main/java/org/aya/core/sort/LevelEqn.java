// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.sort;

import org.aya.api.error.Reporter;
import org.aya.api.ref.Var;
import org.aya.concrete.Expr;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.error.LevelMismatchError;
import org.aya.util.Decision;
import org.aya.util.Ordering;
import org.glavo.kala.collection.Seq;
import org.glavo.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public record LevelEqn(@NotNull Level lhs, @NotNull Level rhs) {
  public Decision biasedEq(@NotNull Ordering cmp) {
    if (lhs.equals(rhs)) return Decision.YES;
    if (lhs instanceof Level.Constant l) {
      if (rhs instanceof Level.Infinity) return cmp == Ordering.Lt ? Decision.YES : Decision.NO;
      else if (rhs instanceof Level.Constant r) return switch (cmp) {
        case Gt -> l.value() >= r.value() ? Decision.YES : Decision.NO;
        case Eq -> l.value() == r.value() ? Decision.YES : Decision.NO;
        case Lt -> l.value() <= r.value() ? Decision.YES : Decision.NO;
      };
    } else if (lhs instanceof Level.Infinity && rhs instanceof Level.Constant) return Decision.NO;
    return Decision.MAYBE;
  }

  /**
   * A set of level equations.
   */
  public record Set(
    @NotNull Buffer<LevelVar> vars,
    @NotNull Reporter reporter,
    @NotNull Buffer<@NotNull LevelEqn> eqns
  ) {
    public void add(@NotNull Sort lhs, @NotNull Sort rhs, @NotNull Ordering cmp, @NotNull Expr loc) {
      insertEqn(loc, cmp, new LevelEqn(lhs.hLevel(), rhs.hLevel()));
      insertEqn(loc, cmp, new LevelEqn(lhs.uLevel(), rhs.uLevel()));
    }

    private void insertEqn(@NotNull Expr loc, @NotNull Ordering cmp, LevelEqn h) {
      switch (h.biasedEq(cmp)) {
        case NO -> {
          reporter.report(new LevelMismatchError(loc, h));
          throw new ExprTycker.TyckInterruptedException();
        }
        case MAYBE -> eqns.append(h);
        case YES -> {
        }
      }
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

    public @Nullable Seq<LevelEqn> solve(@NotNull Map<Var, Integer> solution) {
      throw new UnsupportedOperationException("#93");
    }
  }
}
