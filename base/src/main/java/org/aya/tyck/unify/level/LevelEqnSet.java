// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.unify.level;

import kala.collection.mutable.Buffer;
import kala.collection.mutable.MutableMap;
import org.aya.api.distill.AyaDocile;
import org.aya.api.distill.DistillerOptions;
import org.aya.api.error.SourcePos;
import org.aya.core.sort.LevelSubst;
import org.aya.core.sort.Sort;
import org.aya.generic.Level;
import org.aya.pretty.doc.Doc;
import org.aya.util.Ordering;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

/**
 * A set of level equations.
 *
 * @author ice1000
 */
public record LevelEqnSet(
  @NotNull Buffer<Sort.LvlVar> vars,
  @NotNull Buffer<@NotNull Eqn> eqns,
  @NotNull MutableMap<Sort.LvlVar, @NotNull Sort> solution
) implements LevelSubst.Default {
  public LevelEqnSet() {
    this(Buffer.create(), Buffer.create(), MutableMap.create());
  }

  public void add(@NotNull Sort lhs, @NotNull Sort rhs, @NotNull Ordering cmp, @NotNull SourcePos loc) {
    insertEqn(new Eqn(lhs, rhs, cmp, loc));
  }

  public void add(
    @NotNull Level<Sort.LvlVar> lhs, @NotNull Level<Sort.LvlVar> rhs,
    @NotNull Ordering cmp, @NotNull SourcePos loc
  ) {
    add(new Sort(lhs), new Sort(rhs), cmp, loc);
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
          solution.put(lvlVar, new Sort(new Level.Constant<>(0)));
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
   * For {@link org.aya.tyck.ExprTycker#universe}
   */
  public boolean used(@NotNull Sort.LvlVar var) {
    return eqns.anyMatch(eqn -> eqn.used(var)) ||
      solution.valuesView().anyMatch(level -> Eqn.used(var, level));
  }

  public Sort markUsed(@NotNull Sort.LvlVar universe) {
    return solution.getOrPut(universe, () -> new Sort(new Level.Reference<>(universe)));
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
    @NotNull Sort lhs, @NotNull Sort rhs,
    @NotNull Ordering cmp, @NotNull SourcePos sourcePos
  ) implements AyaDocile {
    public boolean used(@NotNull Sort.LvlVar var) {
      return used(var, lhs) || used(var, rhs);
    }

    public static boolean used(Sort.@NotNull LvlVar var, @NotNull Level<Sort.LvlVar> lvl) {
      return lvl instanceof Level.Reference<Sort.LvlVar> l && l.ref() == var;
    }

    public static boolean used(Sort.@NotNull LvlVar var, @NotNull Sort lvl) {
      return lvl.levels().anyMatch(level -> used(var, level));
    }

    @TestOnly public @NotNull String forZZS(@NotNull Level<Sort.LvlVar> level) {
      return switch (level) {
        case Level.Reference<Sort.LvlVar> ref -> {
          var r = ref.ref();
          yield "new Reference(new Var(\"" + r.name() + "\", " +
            ", 0, " + r.free() + "), " + ref.lift() + ")";
        }
        case Level.Constant<Sort.LvlVar> constant -> "new Const(" + constant.value() + ")";
        case Level.Infinity<Sort.LvlVar> l -> "new Infinity()";
        default -> "";
      };
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

    @Override public @NotNull Doc toDoc(@NotNull DistillerOptions options) {
      return Doc.stickySep(lhs.toDoc(options), Doc.symbol(cmp.symbol), rhs.toDoc(options));
    }
  }
}
