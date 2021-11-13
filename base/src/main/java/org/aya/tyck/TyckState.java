// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import kala.collection.immutable.ImmutableMap;
import kala.collection.mutable.DynamicSeq;
import kala.collection.mutable.MutableMap;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import kala.tuple.Unit;
import org.aya.api.distill.AyaDocile;
import org.aya.api.distill.DistillerOptions;
import org.aya.api.error.Reporter;
import org.aya.util.error.SourcePos;
import org.aya.api.ref.LocalVar;
import org.aya.util.error.WithPos;
import org.aya.core.Meta;
import org.aya.core.term.CallTerm;
import org.aya.core.term.RefTerm;
import org.aya.core.term.Term;
import org.aya.core.visitor.TermConsumer;
import org.aya.core.visitor.VarConsumer;
import org.aya.pretty.doc.Doc;
import org.aya.tyck.error.HoleProblem;
import org.aya.tyck.trace.Trace;
import org.aya.tyck.unify.DefEq;
import org.aya.tyck.unify.level.LevelEqnSet;
import org.aya.util.Ordering;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Currently we only deal with ambiguous equations (so no 'stuck' equations).
 */
public record TyckState(
  @NotNull DynamicSeq<Eqn> eqns,
  @NotNull DynamicSeq<WithPos<Meta>> activeMetas,
  @NotNull LevelEqnSet levelEqns,
  @NotNull MutableMap<@NotNull Meta, @NotNull Term> metas
) {
  public TyckState() {
    this(DynamicSeq.create(), DynamicSeq.create(), new LevelEqnSet(), MutableMap.create());
  }

  public void solveEqn(
    @NotNull Reporter reporter, Trace.@Nullable Builder tracer,
    @NotNull Eqn eqn, boolean allowVague
  ) {
    new DefEq(eqn.cmp, reporter, allowVague, tracer, this, eqn.pos).checkEqn(eqn);
  }

  /** @return true if <code>this.eqns</code> and <code>this.activeMetas</code> are mutated. */
  public boolean simplify(
    @NotNull Reporter reporter, @Nullable Trace.Builder tracer
  ) {
    var removingMetas = DynamicSeq.<WithPos<Meta>>create();
    for (var activeMeta : activeMetas) {
      if (metas.containsKey(activeMeta.data())) {
        eqns.filterInPlace(eqn -> {
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
    activeMetas.filterNotInPlace(removingMetas::contains);
    return removingMetas.isNotEmpty();
  }

  public void solveMetas(@NotNull Reporter reporter, @Nullable Trace.Builder traceBuilder) {
    while (eqns.isNotEmpty()) {
      //noinspection StatementWithEmptyBody
      while (simplify(reporter, traceBuilder)) ;
      // If the standard 'pattern' fragment cannot solve all equations, try to use a nonstandard method
      var eqns = this.eqns.toImmutableSeq();
      if (eqns.isNotEmpty()) {
        for (var eqn : eqns) solveEqn(reporter, traceBuilder, eqn, true);
        reporter.report(new HoleProblem.CannotFindGeneralSolution(eqns));
      }
    }
    levelEqns.solve();
  }

  public void addEqn(@NotNull Eqn eqn) {
    eqns.append(eqn);
    var currentActiveMetas = activeMetas.size();
    eqn.accept(new TermConsumer<>() {
      @Override public Unit visitHole(CallTerm.@NotNull Hole term, Unit unit) {
        var ref = term.ref();
        if (!metas.containsKey(ref))
          activeMetas.append(new WithPos<>(eqn.pos, ref));
        return unit;
      }
    }, Unit.unit());
    assert activeMetas.sizeGreaterThan(currentActiveMetas) : "Adding a bad equation";
  }

  public record Eqn(
    @NotNull Term lhs, @NotNull Term rhs,
    @NotNull Ordering cmp, @NotNull SourcePos pos,
    @NotNull ImmutableMap<@NotNull LocalVar, @NotNull RefTerm> varSubst
  ) implements AyaDocile {
    public <P, R> Tuple2<R, R> accept(@NotNull Term.Visitor<P, R> visitor, P p) {
      return Tuple.of(lhs.accept(visitor, p), rhs.accept(visitor, p));
    }

    public @NotNull Doc toDoc(@NotNull DistillerOptions options) {
      return Doc.stickySep(lhs.toDoc(options), Doc.symbol(cmp.symbol), rhs.toDoc(options));
    }
  }
}
