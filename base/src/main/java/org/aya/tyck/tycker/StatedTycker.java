// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.tycker;

import kala.collection.mutable.MutableTreeSet;
import kala.value.LazyValue;
import org.aya.concrete.Expr;
import org.aya.core.term.RefTerm;
import org.aya.core.term.Term;
import org.aya.core.visitor.Zonker;
import org.aya.generic.util.NormalizeMode;
import org.aya.guest0x0.cubical.Partial;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.TyckState;
import org.aya.tyck.env.LocalCtx;
import org.aya.tyck.trace.Trace;
import org.aya.tyck.unify.Unifier;
import org.aya.util.Ordering;
import org.aya.util.error.SourceNode;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

/**
 * This is the second base-base class of a tycker.
 * It has the zonking stuffs and basic unification functions.
 * Apart from that, it also deals with core term references in concrete terms.
 *
 * @author ice1000
 */
public abstract sealed class StatedTycker extends TracedTycker permits CxlTycker {
  public final @NotNull TyckState state;
  public final @NotNull MutableTreeSet<Expr.WithTerm> withTerms =
    MutableTreeSet.create(Comparator.comparing(SourceNode::sourcePos));

  protected StatedTycker(@NotNull Reporter reporter, @Nullable Trace.Builder traceBuilder, @NotNull TyckState state) {
    super(reporter, traceBuilder);
    this.state = state;
  }

  //region Zonk + solveMetas
  public @NotNull Term zonk(@NotNull Term term) {
    solveMetas();
    return Zonker.make(this).apply(term);
  }

  public @NotNull ExprTycker.Result zonk(@NotNull ExprTycker.Result result) {
    return new ExprTycker.TermResult(zonk(result.wellTyped()), zonk(result.type()));
  }

  public @NotNull Partial<Term> zonk(@NotNull Partial<Term> term) {
    solveMetas();
    return term.fmap(Zonker.make(this));
  }

  public void solveMetas() {
    state.solveMetas(reporter, traceBuilder);
    withTerms.forEach(w -> w.theCore().update(r -> r.freezeHoles(state)));
  }

  public @NotNull Term whnf(@NotNull Term term) {
    return term.normalize(state, NormalizeMode.WHNF);
  }

  public @NotNull Unifier unifier(@NotNull SourcePos pos, @NotNull Ordering ord, @NotNull LocalCtx ctx) {
    return new Unifier(ord, reporter, false, true, traceBuilder, state, pos, ctx);
  }

  protected final void traceExit(ExprTycker.Result result, @NotNull Expr expr) {
    var frozen = LazyValue.of(() -> result.freezeHoles(state));
    tracing(builder -> {
      builder.append(new Trace.TyckT(frozen.get(), expr.sourcePos()));
      builder.reduce();
    });
    if (expr instanceof Expr.WithTerm wt) addWithTerm(wt, frozen.get());
    if (expr instanceof Expr.Lift lift && lift.expr() instanceof Expr.WithTerm wt) addWithTerm(wt, frozen.get());
  }

  protected final void addWithTerm(@NotNull Expr.WithTerm withTerm, @NotNull ExprTycker.Result result) {
    withTerms.add(withTerm);
    withTerm.theCore().set(result);
  }

  public final void addWithTerm(@NotNull Expr.Param param, @NotNull Term type) {
    addWithTerm(param, new ExprTycker.TermResult(new RefTerm(param.ref()), type));
  }
}
