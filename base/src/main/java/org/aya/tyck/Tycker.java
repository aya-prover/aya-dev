// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import org.aya.core.term.Term;
import org.aya.core.visitor.Zonker;
import org.aya.generic.util.NormalizeMode;
import org.aya.guest0x0.cubical.Partial;
import org.aya.tyck.env.LocalCtx;
import org.aya.tyck.trace.Trace;
import org.aya.tyck.unify.Unifier;
import org.aya.util.Ordering;
import org.aya.util.TreeBuilder;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * This is a lightweight abstraction of a tycker,
 * primarily designed to be used in {@link Zonker}.
 * <p>
 * Currently, it seems unnecessary, but maybe we can make some other classes
 * also inherit this class to gain some basic type checker functions.
 *
 * @author ice1000
 */
public abstract class Tycker {
  public final @NotNull Reporter reporter;
  public final @NotNull TyckState state;
  public final @Nullable Trace.Builder traceBuilder;

  protected Tycker(@NotNull Reporter reporter, @NotNull TyckState state, Trace.@Nullable Builder traceBuilder) {
    this.reporter = reporter;
    this.state = state;
    this.traceBuilder = traceBuilder;
  }

  public @NotNull Term zonk(@NotNull Term term) {
    solveMetas();
    return Zonker.make(this).apply(term);
  }

  public @NotNull Partial<Term> zonk(@NotNull Partial<Term> term) {
    solveMetas();
    return term.fmap(Zonker.make(this));
  }

  public void solveMetas() {
    state.solveMetas(reporter, traceBuilder);
  }

  public @NotNull Term whnf(@NotNull Term term) {
    return term.normalize(state, NormalizeMode.WHNF);
  }

  public void tracing(@NotNull Consumer<Trace.@NotNull Builder> consumer) {
    if (traceBuilder != null) consumer.accept(traceBuilder);
  }

  public <R> R traced(@NotNull Supplier<@NotNull Trace> trace, @NotNull Supplier<R> computation) {
    tracing(builder -> builder.shift(trace.get()));
    var res = computation.get();
    tracing(TreeBuilder::reduce);
    return res;
  }

  public @NotNull Unifier unifier(@NotNull SourcePos pos, @NotNull Ordering ord, @NotNull LocalCtx ctx) {
    return new Unifier(ord, reporter, false, true, traceBuilder, state, pos, ctx);
  }
}
