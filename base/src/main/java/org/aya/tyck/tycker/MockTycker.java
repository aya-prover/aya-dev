// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.tycker;

import kala.collection.Map;
import org.aya.concrete.Expr;
import org.aya.core.term.AppTerm;
import org.aya.core.term.PiTerm;
import org.aya.core.term.Term;
import org.aya.core.visitor.Subst;
import org.aya.generic.Constants;
import org.aya.ref.LocalVar;
import org.aya.tyck.Result;
import org.aya.tyck.env.LocalCtx;
import org.aya.tyck.trace.Trace;
import org.aya.tyck.unify.Synthesizer;
import org.aya.tyck.unify.TermComparator;
import org.aya.tyck.unify.Unifier;
import org.aya.util.Arg;
import org.aya.util.Ordering;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * This is the 2.25-th base class of a tycker.
 *
 * @author ice1000
 * @see #generatePi
 * @see #instImplicits(Result, SourcePos)
 * @see #mockArg
 * @see #mockTerm
 */
public abstract sealed class MockTycker extends StatedTycker permits ConcreteAwareTycker, TermComparator {
  /**
   * Never set ctx directly, use {@link MockTycker#subscoped} instead.
   *
   * @see StatedTycker#subscoped(Supplier)
   */
  public @NotNull LocalCtx ctx;

  protected MockTycker(@NotNull Reporter reporter, Trace.@Nullable Builder traceBuilder, @NotNull TyckState state, @NotNull LocalCtx ctx) {
    super(reporter, traceBuilder, state);
    this.ctx = ctx;
  }

  public @NotNull Unifier unifier(@NotNull SourcePos pos, @NotNull Ordering ord) {
    return unifier(pos, ord, ctx);
  }

  public @NotNull Synthesizer synthesizer() {
    return new Synthesizer(state, ctx);
  }

  protected final @NotNull Term mockTerm(Term.Param param, SourcePos pos) {
    // TODO: maybe we should create a concrete hole and check it against the type
    //  in case we can synthesize this term via its type only
    var genName = param.ref().name().concat(Constants.GENERATED_POSTFIX);
    return ctx.freshHole(param.type(), genName, pos).component2();
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
    var domain = ctx.freshTyHole(genName + "ty", pos).component2();
    var codomain = ctx.freshTyHole(genName + "ret", pos).component2();
    return new PiTerm(new Term.Param(new LocalVar(genName, pos), domain, explicit), codomain);
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

  public void addDefEq(@NotNull LocalVar x, @NotNull Term y, @NotNull Term A) {
    ctx.put(x, A);
    state.defEq().addDirectly(x, y);
  }

  public void addDefEqs(@NotNull Subst subst, @NotNull Map<LocalVar, Term> types) {
    types.forEach((var, type) -> ctx.put(var, type));
    state.defEq().addAllDirectly(subst);
  }

  @Override
  public <R> R subscoped(@NotNull Supplier<R> action) {
    var parentCtx = this.ctx;
    this.ctx = parentCtx.deriveMap();
    var result = super.subscoped(action);
    this.ctx = parentCtx;
    return result;
  }
}
