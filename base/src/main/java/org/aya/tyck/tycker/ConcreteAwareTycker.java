// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.tycker;

import kala.collection.mutable.MutableTreeSet;
import kala.value.LazyValue;
import org.aya.concrete.Expr;
import org.aya.core.term.RefTerm;
import org.aya.core.term.Term;
import org.aya.core.visitor.Zonker;
import org.aya.guest0x0.cubical.Partial;
import org.aya.tyck.Result;
import org.aya.tyck.trace.Trace;
import org.aya.util.error.SourceNode;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * This is the 2.5-th base class of a tycker.
 *
 * @see #addWithTerm
 * @see #zonk
 * @see #solveMetas()
 * @see #traceExit
 */
public sealed abstract class ConcreteAwareTycker extends StatedTycker permits MockedTycker {
  public final @NotNull MutableTreeSet<Expr.WithTerm> withTerms =
    MutableTreeSet.create(Comparator.comparing(SourceNode::sourcePos));

  protected ConcreteAwareTycker(@NotNull Reporter reporter, Trace.@Nullable Builder traceBuilder, @NotNull TyckState state) {
    super(reporter, traceBuilder, state);
  }

  //region Zonk + solveMetas
  public void solveMetas() {
    state.solveMetas(reporter, traceBuilder);
    withTerms.forEach(w -> w.theCore().update(r -> r.freezeHoles(state)));
  }

  public @NotNull Term zonk(@NotNull Term term) {
    solveMetas();
    return Zonker.make(this).apply(term);
  }

  public @NotNull Result zonk(@NotNull Result result) {
    return new Result.Default(zonk(result.wellTyped()), zonk(result.type()));
  }

  public @NotNull Partial<Term> zonk(@NotNull Partial<Term> term) {
    solveMetas();
    return term.fmap(Zonker.make(this));
  }

  protected final <R extends Result> R traced(
    @NotNull Supplier<Trace> trace,
    @NotNull Expr expr, @NotNull Function<Expr, R> tyck
  ) {
    tracing(builder -> builder.shift(trace.get()));
    var result = tyck.apply(expr);
    traceExit(result, expr);
    return result;
  }

  protected final void traceExit(Result result, @NotNull Expr expr) {
    var frozen = LazyValue.of(() -> result.freezeHoles(state));
    tracing(builder -> {
      builder.append(new Trace.TyckT(frozen.get(), expr.sourcePos()));
      builder.reduce();
    });
    if (expr instanceof Expr.WithTerm wt) addWithTerm(wt, frozen.get());
    if (expr instanceof Expr.Lift lift && lift.expr() instanceof Expr.WithTerm wt) addWithTerm(wt, frozen.get());
  }

  protected final void addWithTerm(@NotNull Expr.WithTerm withTerm, @NotNull Result result) {
    withTerms.add(withTerm);
    withTerm.theCore().set(result);
  }

  public final void addWithTerm(@NotNull Expr.Param param, @NotNull Term type) {
    addWithTerm(param, new Result.Default(new RefTerm(param.ref()), type));
  }
}
