// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.sort;

import org.aya.api.error.Reporter;
import org.aya.concrete.Expr;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.error.LevelMismatchError;
import org.aya.util.Decision;
import org.aya.util.Ordering;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.collection.mutable.MutableMap;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public record LevelEqn(@NotNull Level lhs, @NotNull Level rhs) {
  public Decision biasedEq(@NotNull Ordering cmp) {
    if (lhs.equals(rhs)) return Decision.YES;
    if (lhs instanceof Level.Constant l) {
      if (rhs instanceof Level.Infinity) return Decision.bool(cmp == Ordering.Lt);
      else if (rhs instanceof Level.Constant r) return switch (cmp) {
        case Gt -> Decision.bool(l.value() >= r.value());
        case Eq -> Decision.bool(l.value() == r.value());
        case Lt -> Decision.bool(l.value() <= r.value());
      };
    } else if (lhs instanceof Level.Infinity && rhs instanceof Level.Constant)
      return Decision.bool(cmp == Ordering.Gt);
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

    public @NotNull LevelSubst.Simple solve() {
      var map = new LevelSubst.Simple(MutableMap.of());
      solve(map.map());
      return map;
    }

    public void solve(@NotNull MutableMap<LevelVar, Level> solution) {
      throw new UnsupportedOperationException("#93");
    }
  }
}
