// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import kala.collection.mutable.MutableSet;
import org.aya.core.Meta;
import org.aya.core.def.PrimDef;
import org.aya.core.term.MetaTerm;
import org.aya.core.term.Term;
import org.aya.core.visitor.TermConsumer;
import org.aya.core.visitor.TermFolder;
import org.aya.generic.AyaDocile;
import org.aya.pretty.doc.Doc;
import org.aya.tyck.env.LocalCtx;
import org.aya.tyck.error.HoleProblem;
import org.aya.tyck.trace.Trace;
import org.aya.tyck.unify.Unifier;
import org.aya.util.Ordering;
import org.aya.util.prettier.PrettierOptions;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Currently we only deal with ambiguous equations (so no 'stuck' equations).
 */
public record TyckState(
  @NotNull MutableList<Eqn> eqns,
  @NotNull MutableList<WithPos<Meta>> activeMetas,
  @NotNull MutableMap<@NotNull Meta, @NotNull Term> metas,
  @NotNull MutableSet<@NotNull Meta> metaNotProps,
  @NotNull PrimDef.Factory primFactory
) {
  public TyckState(@NotNull PrimDef.Factory primFactory) {
    this(MutableList.create(), MutableList.create(), MutableMap.create(), MutableSet.create(), primFactory);
  }

  /**
   * @param trying whether to solve in a yasashi manner.
   */
  public void solveEqn(
    @NotNull Reporter reporter, Trace.@Nullable Builder tracer,
    @NotNull Eqn eqn, boolean trying
  ) {
    new Unifier(eqn.cmp, reporter, !trying, trying, tracer, this, eqn.pos, eqn.localCtx).checkEqn(eqn);
  }

  /** @return true if <code>this.eqns</code> and <code>this.activeMetas</code> are mutated. */
  public boolean simplify(
    @NotNull Reporter reporter, @Nullable Trace.Builder tracer
  ) {
    var removingMetas = MutableList.<WithPos<Meta>>create();
    for (var activeMeta : activeMetas) {
      if (metas.containsKey(activeMeta.data())) {
        var usageCounter = new TermFolder.Usages(activeMeta.data());
        eqns.retainIf(eqn -> {
          if (usageCounter.apply(eqn.lhs) + usageCounter.apply(eqn.rhs) > 0) {
            solveEqn(reporter, tracer, eqn, true);
            return false;
          } else return true;
        });
        removingMetas.append(activeMeta);
      }
    }
    activeMetas.removeIf(removingMetas::contains);
    return removingMetas.isNotEmpty();
  }

  public void solveMetas(@NotNull Reporter reporter, @Nullable Trace.Builder traceBuilder) {
    while (eqns.isNotEmpty()) {
      //noinspection StatementWithEmptyBody
      while (simplify(reporter, traceBuilder)) ;
      // If the standard 'pattern' fragment cannot solve all equations, try to use a nonstandard method
      var eqns = this.eqns.toImmutableSeq();
      if (eqns.isNotEmpty()) {
        for (var eqn : eqns) solveEqn(reporter, traceBuilder, eqn, false);
        reporter.report(new HoleProblem.CannotFindGeneralSolution(eqns));
      }
    }
  }

  public void addEqn(@NotNull Eqn eqn) {
    eqns.append(eqn);
    var currentActiveMetas = activeMetas.size();
    var consumer = new TermConsumer() {
      @Override public void pre(@NotNull Term tm) {
        if (tm instanceof MetaTerm hole && !metas.containsKey(hole.ref()))
            activeMetas.append(new WithPos<>(eqn.pos, hole.ref()));
        TermConsumer.super.pre(tm);
      }
    };
    consumer.accept(eqn.lhs);
    consumer.accept(eqn.rhs);
    assert activeMetas.sizeGreaterThan(currentActiveMetas) : "Adding a bad equation";
  }

  public record Eqn(
    @NotNull Term lhs, @NotNull Term rhs,
    @NotNull Ordering cmp, @NotNull SourcePos pos,
    @NotNull LocalCtx localCtx,
    @NotNull Unifier.Sub lr, @NotNull Unifier.Sub rl
  ) implements AyaDocile {
    public @NotNull Doc toDoc(@NotNull PrettierOptions options) {
      return Doc.stickySep(lhs.toDoc(options), Doc.symbol(cmp.symbol), rhs.toDoc(options));
    }
  }
}
