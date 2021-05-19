// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.sort;

import org.aya.api.error.Reporter;
import org.aya.api.error.SourcePos;
import org.aya.generic.Level;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Docile;
import org.aya.util.Ordering;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.collection.mutable.MutableMap;
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

    @Override public @NotNull Doc toDoc() {
      return Doc.hsep(lhs.toDoc(), Doc.symbol(cmp.symbol), rhs.toDoc());
    }
  }
}
