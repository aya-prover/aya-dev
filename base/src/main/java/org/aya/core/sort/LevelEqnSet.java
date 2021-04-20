// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.sort;

import org.aya.api.error.Reporter;
import org.aya.api.error.SourcePos;
import org.aya.generic.Level;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Docile;
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

  public void solve() {
    var newEqns = Buffer.from(eqns);
    eqns.clear();
    newEqns.view().map(this::applyTo).filterTo(eqns, this::solveEqn);
  }

  public boolean constraints(@NotNull Sort.LvlVar var) {
    return eqns.anyMatch(eqn -> eqn.constraints(var)) ||
      solution.valuesView().anyMatch(level -> Eqn.constraints(var, level));
  }

  private boolean solveEqn(@NotNull LevelEqnSet.Eqn eqn) {
    if (eqn.biasedEq(Ordering.Eq) == Decision.YES) return false;
    if (eqn.lhs instanceof Level.Reference<Sort.LvlVar> lhs) {
      if (lhs.ref().free()) {
        solution.put(lhs.ref(), eqn.rhs.lift(-lhs.lift()));
        return false;
      }
    }
    if (eqn.rhs instanceof Level.Reference<Sort.LvlVar> rhs) {
      if (rhs.ref().free()) {
        solution.put(rhs.ref(), eqn.lhs.lift(rhs.lift()));
        return false;
      }
    }
    return true;
  }

  public Level<Sort.LvlVar> markUsed(@NotNull Sort.LvlVar universe) {
    var level = new Level.Reference<>(universe);
    solution.put(universe, level);
    return level;
  }

  /**
   * @author ice1000
   */
  @Debug.Renderer(text = "toDoc().debugRender()")
  public static record Eqn(@NotNull Level<Sort.LvlVar> lhs, @NotNull Level<Sort.LvlVar> rhs) implements Docile {
    public Decision biasedEq(@NotNull Ordering cmp) {
      if (lhs.equals(rhs)) return Decision.YES;
      if (rhs instanceof Level.Infinity) return lhs instanceof Level.Infinity
        ? Decision.YES : Decision.optimistic(cmp == Ordering.Lt);
      if (lhs instanceof Level.Infinity) return Decision.optimistic(cmp == Ordering.Gt);
      if (lhs instanceof Level.Constant<Sort.LvlVar> l) {
        if (rhs instanceof Level.Constant<Sort.LvlVar> r) return switch (cmp) {
          case Gt -> Decision.confident(l.value() >= r.value());
          case Eq -> Decision.confident(l.value() == r.value());
          case Lt -> Decision.confident(l.value() <= r.value());
        };
      }
      return Decision.MAYBE;
    }

    public boolean constraints(@NotNull Sort.LvlVar var) {
      return constraints(var, lhs) || constraints(var, rhs);
    }

    public static boolean constraints(Sort.@NotNull LvlVar var, @NotNull Level<Sort.LvlVar> lvl) {
      return lvl instanceof Level.Reference<Sort.LvlVar> l && l.ref() == var;
    }

    @Override public @NotNull Doc toDoc() {
      return Doc.hcat(lhs.toDoc(), Doc.symbol(" = "), rhs.toDoc());
    }
  }
}
