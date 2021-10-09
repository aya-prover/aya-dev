// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.sort;

import kala.collection.mutable.MutableMap;
import kala.collection.mutable.MutableTreeMap;
import kala.control.Option;
import org.aya.generic.Level;
import org.aya.tyck.unify.level.LevelEqnSet;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 * @see Default
 * @see Simple
 * @see LevelEqnSet
 */
public interface LevelSubst {
  boolean isEmpty();
  @NotNull Option<Sort> get(@NotNull Sort.LvlVar var);
  @NotNull LevelSubst subst(@NotNull LevelSubst subst);
  @NotNull LevelSubst EMPTY = new Simple(MutableTreeMap.of((o1, o2) -> {
    throw new UnsupportedOperationException("Shall not modify LevelSubst.EMPTY");
  }));

  default @NotNull Sort applyTo(@NotNull Level<Sort.LvlVar> lvl) {
    if (lvl instanceof Level.Reference<Sort.LvlVar> ref)
      return get(ref.ref()).map(n -> n.lift(ref.lift())).getOrDefault(new Sort(ref));
    else return new Sort(lvl);
  }

  default @NotNull Sort applyTo(@NotNull Sort lvl) {
    return Sort.merge(lvl.levels().map(this::applyTo));
  }

  default @NotNull LevelEqnSet.Eqn applyTo(@NotNull LevelEqnSet.Eqn eqn) {
    var lhs = applyTo(eqn.lhs());
    var rhs = applyTo(eqn.rhs());
    return lhs == eqn.lhs() && rhs == eqn.rhs() ? eqn : new LevelEqnSet.Eqn(lhs, rhs, eqn.cmp(), eqn.sourcePos());
  }

  record Simple(@NotNull MutableMap<Sort.@NotNull LvlVar, @NotNull Sort> solution) implements Default {
  }

  interface Default extends LevelSubst {
    @NotNull MutableMap<Sort.@NotNull LvlVar, @NotNull Sort> solution();

    @Override default boolean isEmpty() {
      return solution().isEmpty();
    }

    @Override default @NotNull Option<@NotNull Sort> get(@NotNull Sort.LvlVar var) {
      return solution().getOption(var);
    }

    @Override default @NotNull LevelSubst subst(@NotNull LevelSubst subst) {
      if (isEmpty()) return this;
      solution().replaceAll((var, term) -> subst.applyTo(term));
      return this;
    }
  }
}
