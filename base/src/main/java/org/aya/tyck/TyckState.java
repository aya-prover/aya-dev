// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import kala.collection.mutable.Buffer;
import kala.collection.mutable.MutableMap;
import kala.tuple.Unit;
import org.aya.api.error.Reporter;
import org.aya.api.util.WithPos;
import org.aya.core.Meta;
import org.aya.core.term.Term;
import org.aya.core.visitor.VarConsumer;
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

  /** @return true if <code>this.termEqns</code> is mutated. */
  public boolean simplify(
    @NotNull Reporter reporter, @Nullable Trace.Builder tracer
  ) {
    var removingMetas = Buffer.<WithPos<Meta>>create();
    for (var activeMeta : termEqns.activeMetas()) {
      if (metas.containsKey(activeMeta.data())) {
        termEqns.eqns().filterInPlace(eqn -> {
          var usageCounter = new VarConsumer.UsageCounter(activeMeta.data());
          eqn.accept(usageCounter, Unit.unit());
          if (usageCounter.usageCount() > 0) {
            solveEqn(reporter, tracer, eqn, false);
            return false;
          } else return true;
        });
        removingMetas.append(activeMeta);
      }
    }
    termEqns.activeMetas().filterNotInPlace(removingMetas::contains);
    return removingMetas.isNotEmpty();
  }

  public void solveMetas(@NotNull Reporter reporter, @Nullable Trace.Builder traceBuilder) {
    while (termEqns.eqns().isNotEmpty()) {
      //noinspection StatementWithEmptyBody
      while (simplify(reporter, traceBuilder)) ;
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
