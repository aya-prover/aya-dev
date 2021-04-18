// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.sort;

import org.aya.api.error.Reporter;
import org.aya.api.error.SourcePos;
import org.aya.generic.Level;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.error.LevelMismatchError;
import org.aya.util.Decision;
import org.aya.util.Ordering;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.collection.mutable.MutableMap;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;

/**
 * A set of level equations.
 *
 * @author ice1000
 */
public record LevelEqnSet(
  @NotNull Buffer<Sort.LvlVar> vars,
  @NotNull Reporter reporter,
  @NotNull Buffer<@NotNull Eqn> eqns,
  @NotNull MutableMap<Sort.LvlVar, Level<Sort.LvlVar>> solution
) implements LevelSubst.Default {
  public LevelEqnSet(@NotNull Reporter reporter) {
    this(Buffer.of(), reporter, Buffer.of(), MutableMap.of());
  }

  public void add(@NotNull Sort lhs, @NotNull Sort rhs, @NotNull Ordering cmp, @NotNull SourcePos loc) {
    add(lhs.hLevel(), rhs.hLevel(), cmp, loc);
    add(lhs.uLevel(), rhs.uLevel(), cmp, loc);
  }

  public void add(
    @NotNull Level<Sort.LvlVar> lhs, @NotNull Level<Sort.LvlVar> rhs,
    @NotNull Ordering cmp, @NotNull SourcePos loc
  ) {
    insertEqn(loc, cmp, new Eqn(lhs, rhs));
  }

  private void insertEqn(@NotNull SourcePos loc, @NotNull Ordering cmp, Eqn h) {
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

  public void add(@NotNull LevelEqnSet other) {
    vars.appendAll(other.vars);
    eqns.appendAll(other.eqns);
  }

  public void clear() {
    vars.clear();
    eqns.clear();
  }

  public void solve() {
    var newEqns = Buffer.from(eqns);
    eqns.clear();
    newEqns.view().map(this::applyTo).filterTo(eqns, this::solveEqn);
  }

  private boolean solveEqn(@NotNull LevelEqnSet.Eqn eqn) {
    if (eqn.lhs() instanceof Level.Reference<Sort.LvlVar> lhs) {
      if (!lhs.ref().bound()) {
        solution.put(lhs.ref(), eqn.rhs());
        return true;
      } else if (eqn.rhs() instanceof Level.Reference<Sort.LvlVar> rhs) {
        if (!rhs.ref().bound()) {
          solution.put(rhs.ref(), lhs);
          return true;
        }
      }
    } else if (eqn.lhs() instanceof Level.Constant<Sort.LvlVar> lhs) {
      if (eqn.rhs() instanceof Level.Reference<Sort.LvlVar> rhs) {
        if (!rhs.ref().bound()) {
          solution.put(rhs.ref(), lhs);
          return true;
        }
      }
    }
    return false;
  }

  /**
   * @author ice1000
   */
  @Debug.Renderer(text = "lhs.toDoc().debugRender() + \" = \" + rhs.toDoc().debugRender()")
  public static record Eqn(@NotNull Level<Sort.LvlVar> lhs, @NotNull Level<Sort.LvlVar> rhs) {
    public Decision biasedEq(@NotNull Ordering cmp) {
      if (lhs.equals(rhs)) return Decision.YES;
      if (lhs instanceof Level.Constant<Sort.LvlVar> l) {
        if (rhs instanceof Level.Infinity) return Decision.bool(cmp == Ordering.Lt);
        else if (rhs instanceof Level.Constant<Sort.LvlVar> r) return switch (cmp) {
          case Gt -> Decision.bool(l.value() >= r.value());
          case Eq -> Decision.bool(l.value() == r.value());
          case Lt -> Decision.bool(l.value() <= r.value());
        };
      } else if (lhs instanceof Level.Infinity && rhs instanceof Level.Constant)
        return Decision.bool(cmp == Ordering.Gt);
      return Decision.MAYBE;
    }
  }
}
