// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.tycker;

import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import kala.collection.mutable.MutableSet;
import org.aya.core.Meta;
import org.aya.core.def.PrimDef;
import org.aya.core.term.ErrorTerm;
import org.aya.core.term.MetaTerm;
import org.aya.core.term.SortTerm;
import org.aya.core.term.Term;
import org.aya.core.visitor.TermConsumer;
import org.aya.core.visitor.TermFolder;
import org.aya.generic.AyaDocile;
import org.aya.generic.util.NormalizeMode;
import org.aya.pretty.doc.Doc;
import org.aya.tyck.env.LocalCtx;
import org.aya.tyck.error.HoleProblem;
import org.aya.tyck.pat.TypedSubst;
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
public class TyckState {
  public final @NotNull MutableList<Eqn> eqns;
  public final @NotNull MutableList<WithPos<Meta>> activeMetas;
  public final @NotNull MutableMap<@NotNull Meta, @NotNull Term> metas;
  public final @NotNull MutableSet<@NotNull Meta> notInPropMetas;
  public final @NotNull PrimDef.Factory primFactory;
  public @NotNull TypedSubst definitionEqualities;

  public TyckState(@NotNull MutableList<Eqn> eqns,
                   @NotNull MutableList<WithPos<Meta>> activeMetas,
                   @NotNull MutableMap<@NotNull Meta, @NotNull Term> metas,
                   @NotNull MutableSet<@NotNull Meta> notInPropMetas,
                   @NotNull TypedSubst definitionEqualities,
                   @NotNull PrimDef.Factory primFactory) {
    this.eqns = eqns;
    this.activeMetas = activeMetas;
    this.metas = metas;
    this.notInPropMetas = notInPropMetas;
    this.definitionEqualities = definitionEqualities;
    this.primFactory = primFactory;
  }

  public TyckState(@NotNull PrimDef.Factory primFactory) {
    this(MutableList.create(), MutableList.create(), MutableMap.create(), MutableSet.create(), new TypedSubst(), primFactory);
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

  public boolean solve(@NotNull Meta meta, @NotNull Term t) {
    if (t.findUsages(meta) > 0) return false;
    if (notInPropMetas.contains(meta)) {
      var term = t.normalize(this, NormalizeMode.WHNF);
      if (!(term instanceof ErrorTerm)) {
        if (!(term instanceof SortTerm sort)) throw new IllegalStateException("expected a sort: " + t);
        if (sort.isProp()) throw new IllegalStateException("expected a non-Prop sort"); // TODO: better reporting
      }
    }
    metas().put(meta, t);
    return true;
  }

  /// region Record Adapter
  // TODO: do we really need these?

  public MutableList<Eqn> eqns() {
    return eqns;
  }

  public MutableList<WithPos<Meta>> activeMetas() {
    return activeMetas;
  }

  public MutableMap<Meta, Term> metas() {
    return metas;
  }

  public MutableSet<Meta> notInPropMetas() {
    return notInPropMetas;
  }

  public TypedSubst definitionEqualities() {
    return definitionEqualities;
  }

  public PrimDef.Factory primFactory() {
    return primFactory;
  }


  /// endregion

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
