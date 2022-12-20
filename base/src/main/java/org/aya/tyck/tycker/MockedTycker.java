// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.tycker;

import org.aya.concrete.Expr;
import org.aya.core.term.*;
import org.aya.generic.Constants;
import org.aya.ref.LocalVar;
import org.aya.tyck.Result;
import org.aya.tyck.env.LocalCtx;
import org.aya.tyck.env.MapLocalCtx;
import org.aya.tyck.trace.Trace;
import org.aya.tyck.unify.Unifier;
import org.aya.util.Arg;
import org.aya.util.Ordering;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * This is the third base-base class of a tycker.
 * It has a localCtx and supports some term mocking functions.
 *
 * @author ice1000
 * @see #generatePi
 * @see #instImplicits
 * @see #mockArg
 * @see #mockTerm
 * @see #subscoped(Supplier)
 */
public abstract sealed class MockedTycker extends StatedTycker permits UnifiedTycker {
  public @NotNull LocalCtx localCtx = new MapLocalCtx();

  protected MockedTycker(@NotNull Reporter reporter, Trace.@Nullable Builder traceBuilder, @NotNull TyckState state) {
    super(reporter, traceBuilder, state);
  }

  public @NotNull Unifier unifier(@NotNull SourcePos pos, @NotNull Ordering ord) {
    return unifier(pos, ord, localCtx);
  }

  protected final @NotNull Term mockTerm(Term.Param param, SourcePos pos) {
    // TODO: maybe we should create a concrete hole and check it against the type
    //  in case we can synthesize this term via its type only
    var genName = param.ref().name().concat(Constants.GENERATED_POSTFIX);
    return localCtx.freshHole(param.type(), genName, pos)._2;
  }

  protected final @NotNull Arg<Term> mockArg(Term.Param param, SourcePos pos) {
    return new Arg<>(mockTerm(param, pos), param.explicit());
  }

  protected final @NotNull Term generatePi(Expr.@NotNull Lambda expr) {
    var param = expr.param();
    return generatePi(expr.sourcePos(), param.ref().name(), param.explicit());
  }

  private @NotNull Term generatePi(@NotNull SourcePos pos, @NotNull String name, boolean explicit) {
    var genName = name + Constants.GENERATED_POSTFIX;
    // [ice]: unsure if ZERO is good enough
    var domain = localCtx.freshHole(SortTerm.Type0, genName + "ty", pos)._2;
    var codomain = localCtx.freshHole(SortTerm.Type0, pos)._2;
    return new PiTerm(new Term.Param(new LocalVar(genName, pos), domain, explicit), codomain);
  }

  protected final Term instImplicits(@NotNull Term term, @NotNull SourcePos pos) {
    term = whnf(term);
    while (term instanceof LamTerm intro && !intro.param().explicit()) {
      term = whnf(AppTerm.make(intro, mockArg(intro.param(), pos)));
    }
    return term;
  }

  protected final Result instImplicits(@NotNull Result result, @NotNull SourcePos pos) {
    var type = whnf(result.type());
    var term = result.wellTyped();
    while (type instanceof PiTerm pi && !pi.param().explicit()) {
      var holeApp = mockArg(pi.param(), pos);
      term = AppTerm.make(term, holeApp);
      type = whnf(pi.substBody(holeApp.term()));
    }
    return new Result.Default(term, type);
  }

  public <R> R subscoped(@NotNull Supplier<R> action) {
    var parent = this.localCtx;
    this.localCtx = parent.deriveMap();
    var result = action.get();
    this.localCtx = parent;
    return result;
  }
}
