// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.tycker;

import org.aya.core.def.PrimDef;
import org.aya.core.repr.AyaShape;
import org.aya.core.term.ErrorTerm;
import org.aya.core.term.Term;
import org.aya.generic.AyaDocile;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.Result;
import org.aya.tyck.StmtTycker;
import org.aya.tyck.trace.Trace;
import org.aya.util.TreeBuilder;
import org.aya.util.reporter.Problem;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * This is the base-base class of a tycker.
 * It has the error reporting functionality and the tracing stuffs.
 *
 * @author ice1000
 * @see #tracing
 * @see #traced
 */
public sealed abstract class TracedTycker permits StmtTycker, StatedTycker {
  public final @NotNull Reporter reporter;
  public final @Nullable Trace.Builder traceBuilder;

  protected TracedTycker(@NotNull Reporter reporter, Trace.@Nullable Builder traceBuilder) {
    this.reporter = reporter;
    this.traceBuilder = traceBuilder;
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

  protected final @NotNull Result fail(@NotNull AyaDocile expr, @NotNull Problem prob) {
    return fail(expr, ErrorTerm.typeOf(expr), prob);
  }

  protected final @NotNull Result fail(@NotNull AyaDocile expr, @NotNull Term term, @NotNull Problem prob) {
    reporter.report(prob);
    return new Result.Default(new ErrorTerm(expr), term);
  }

  public @NotNull ExprTycker newTycker(@NotNull PrimDef.Factory primFactory, @NotNull AyaShape.Factory literalShapes) {
    return new ExprTycker(primFactory, literalShapes, reporter, traceBuilder);
  }
}
