// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import kala.collection.mutable.MutableMap;
import org.aya.api.error.Reporter;
import org.aya.core.Meta;
import org.aya.core.term.Term;
import org.aya.tyck.error.HoleProblem;
import org.aya.tyck.trace.Trace;
import org.aya.tyck.unify.DefEq;
import org.aya.tyck.unify.EqnSet;
import org.aya.tyck.unify.level.LevelEqnSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record TyckState(
  @NotNull EqnSet termEqns,
  @NotNull LevelEqnSet levelEqns,
  @NotNull MutableMap<@NotNull Meta, @NotNull Term> metas
) {
  public TyckState() {
    this(new EqnSet(), new LevelEqnSet(), MutableMap.create());
  }

  public void solveEqn(
    @NotNull Reporter reporter, Trace.@Nullable Builder tracer,
    @NotNull EqnSet.Eqn eqn, boolean allowVague
  ) {
    new DefEq(eqn.cmp(), reporter, allowVague, tracer, this, eqn.pos()).checkEqn(eqn);
  }

  public void solveMetas(@NotNull Reporter reporter, @Nullable Trace.Builder traceBuilder) {
    while (termEqns.eqns().isNotEmpty()) {
      //noinspection StatementWithEmptyBody
      while (termEqns.simplify(this, reporter, traceBuilder)) ;
      // If the standard 'pattern' fragment cannot solve all equations, try to use a nonstandard method
      var eqns = termEqns.eqns().toImmutableSeq();
      if (eqns.isNotEmpty()) {
        for (var eqn : eqns) solveEqn(reporter, traceBuilder, eqn, true);
        reporter.report(new HoleProblem.CannotFindGeneralSolution(eqns));
      }
    }
    levelEqns.solve();
  }
}
