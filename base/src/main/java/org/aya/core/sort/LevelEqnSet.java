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
    insertEqn(new Eqn(lhs, rhs, cmp, loc));
  }

  private void insertEqn(Eqn h) {
    switch (h.biasedEq()) {
      case NO -> {
        reporter.report(new LevelMismatchError(h));
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
    var solutionSize = solution.size();
    newEqns.view().map(this::applyTo).filterTo(eqns, this::solveEqn);
    if (solutionSize != solution.size()) subst(this);
  }

  public void reportAll() {
    for (var eqn : eqns) reporter.report(new LevelMismatchError(eqn));
  }

  public boolean constraints(@NotNull Sort.LvlVar var) {
    return eqns.anyMatch(eqn -> eqn.constraints(var)) ||
      solution.valuesView().anyMatch(level -> Eqn.constraints(var, level));
  }

  private boolean solveEqn(@NotNull LevelEqnSet.Eqn eqn) {
    if (eqn.biasedEq() == Decision.YES) return false;
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
    return solution.getOrPut(universe, () -> new Level.Reference<>(universe));
  }

  /**
   * @author ice1000
   */
  @Debug.Renderer(text = "toDoc().debugRender()")
  public static record Eqn(
    @NotNull Level<Sort.LvlVar> lhs, @NotNull Level<Sort.LvlVar> rhs,
    @NotNull Ordering cmp, @NotNull SourcePos sourcePos
  ) implements Docile {
    public @NotNull Decision biasedEq() {
      if (lhs.equals(rhs)) return Decision.YES;
      if (rhs instanceof Level.Infinity) return lhs instanceof Level.Infinity
        ? Decision.YES : Decision.optimistic(cmp == Ordering.Lt);
      if (lhs instanceof Level.Infinity) return Decision.optimistic(cmp == Ordering.Gt);
      if (lhs instanceof Level.Constant<Sort.LvlVar> l) {
        if (rhs instanceof Level.Constant<Sort.LvlVar> r) return decide(l.value(), r.value());
        if (l.value() == 0 && rhs instanceof Level.Reference<Sort.LvlVar> r && !r.ref().free())
          return Decision.optimistic(cmp == Ordering.Lt);
      }
      if (lhs instanceof Level.Reference<Sort.LvlVar> l) {
        if (!l.ref().free() && rhs instanceof Level.Constant<Sort.LvlVar> r && r.value() == 0)
          return Decision.optimistic(cmp == Ordering.Gt);
        if (rhs instanceof Level.Reference<Sort.LvlVar> r && l.ref() == r.ref()) return decide(l.lift(), r.lift());
      }
      return Decision.MAYBE;
    }

    private @NotNull Decision decide(int lv, int rv) {
      return switch (cmp) {
        case Gt -> Decision.confident(lv >= rv);
        case Eq -> Decision.confident(lv == rv);
        case Lt -> Decision.confident(lv <= rv);
      };
    }

    public boolean constraints(@NotNull Sort.LvlVar var) {
      return constraints(var, lhs) || constraints(var, rhs);
    }

    public static boolean constraints(Sort.@NotNull LvlVar var, @NotNull Level<Sort.LvlVar> lvl) {
      return lvl instanceof Level.Reference<Sort.LvlVar> l && l.ref() == var;
    }

    @Override public @NotNull Doc toDoc() {
      return Doc.hsep(lhs.toDoc(), Doc.symbol(cmp.symbol), rhs.toDoc());
    }
  }
}
