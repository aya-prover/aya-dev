// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.sort;

import org.aya.api.error.Reporter;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.LevelGenVar;
import org.aya.generic.Level;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Docile;
import org.aya.util.Ordering;
import kala.collection.mutable.Buffer;
import kala.collection.mutable.MutableMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

/**
 * A set of level equations.
 *
 * @author ice1000
 */
public record LevelEqnSet(
  @NotNull Buffer<Sort.LvlVar> vars,
  @NotNull Reporter reporter,
  @NotNull Buffer<@NotNull Eqn> eqns,
  @NotNull MutableMap<Sort.LvlVar, Sort.CoreLevel> solution
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
    add(new Sort.CoreLevel(lhs), new Sort.CoreLevel(rhs), cmp, loc);
  }

  public void add(@NotNull Sort.CoreLevel lhs, @NotNull Sort.CoreLevel rhs, @NotNull Ordering cmp, @NotNull SourcePos loc) {
    insertEqn(new Eqn(lhs, rhs, cmp, loc));
  }

  private void insertEqn(Eqn h) {
    eqns.append(h);
  }

  public void solve() {
    var solver = new LevelSolver();
    try {
      solver.solve(this);
      for (var lvlVar : vars)
        if (!solution.containsKey(lvlVar)) {
          solution.put(lvlVar, new Sort.CoreLevel(new Level.Constant<>(lvlVar.kind().defaultValue)));
        }
      eqns.clear();
    } catch (LevelSolver.UnsatException ignored) {
      // Level unsolved, leave the 'useful' equations
      var buf = Buffer.<Eqn>create();
      eqns.filterNotTo(buf, solver.avoidableEqns::contains);
      eqns.clear();
      eqns.appendAll(buf);
    }
  }

  /**
   * For {@link org.aya.tyck.ExprTycker#universe} and {@link org.aya.tyck.ExprTycker#homotopy}
   */
  public boolean used(@NotNull Sort.LvlVar var) {
    return eqns.anyMatch(eqn -> eqn.used(var)) ||
      solution.valuesView().anyMatch(level -> Eqn.used(var, level));
  }

  public Sort.CoreLevel markUsed(@NotNull Sort.LvlVar universe) {
    return solution.getOrPut(universe, () -> new Sort.CoreLevel(new Level.Reference<>(universe)));
  }

  @TestOnly public @NotNull String forZZS() {
    var builder = new StringBuilder("List.of(");
    boolean started = false;
    for (var eqn : eqns) {
      if (started) builder.append(", ");
      started = true;
      eqn.forZZS(builder);
    }
    return builder.append(")").toString();
  }

  /**
   * @author ice1000
   */
  public static record Eqn(
    @NotNull Sort.CoreLevel lhs, @NotNull Sort.CoreLevel rhs,
    @NotNull Ordering cmp, @NotNull SourcePos sourcePos
  ) implements Docile {
    public boolean used(@NotNull Sort.LvlVar var) {
      return used(var, lhs) || used(var, rhs);
    }

    public static boolean used(Sort.@NotNull LvlVar var, @NotNull Level<Sort.LvlVar> lvl) {
      return lvl instanceof Level.Reference<Sort.LvlVar> l && l.ref() == var;
    }

    public static boolean used(Sort.@NotNull LvlVar var, @NotNull Sort.CoreLevel lvl) {
      return lvl.levels().anyMatch(level -> used(var, level));
    }

    @TestOnly public @NotNull String forZZS(@NotNull Level<Sort.LvlVar> level) {
      if (level instanceof Level.Reference<Sort.LvlVar> reference) {
        var r = reference.ref();
        return "new Reference(new Var(\"" + r.name() + "\", " + (r.kind() == LevelGenVar.Kind.Homotopy) + ", " + r.kind().defaultValue + ", " + r.free() + "), " + reference.lift() + ")";
      } else if (level instanceof Level.Constant<Sort.LvlVar> constant) {
        return "new Const(" + constant.value() + ")";
      } else if (level instanceof Level.Infinity<Sort.LvlVar>) {
        return "new Infinity()";
      } else return "";
    }

    @TestOnly public void forZZS(@NotNull StringBuilder builder) {
      builder.append("new Equation(Ord.")
        .append(cmp.name())
        .append(", new Max(List.of(");
      lhs.levels().joinTo(builder, ", ", this::forZZS);
      builder.append(")), new Max(List.of(");
      rhs.levels().joinTo(builder, ", ", this::forZZS);
      builder.append(")))");
    }

    @Override public @NotNull Doc toDoc() {
      return Doc.hsep(lhs.toDoc(), Doc.symbol(cmp.symbol), rhs.toDoc());
    }
  }
}
