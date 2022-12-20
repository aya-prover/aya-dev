// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.tycker;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.Expr;
import org.aya.core.term.*;
import org.aya.core.visitor.Subst;
import org.aya.generic.Constants;
import org.aya.guest0x0.cubical.CofThy;
import org.aya.guest0x0.cubical.Partial;
import org.aya.guest0x0.cubical.Restr;
import org.aya.ref.LocalVar;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.TyckState;
import org.aya.tyck.env.LocalCtx;
import org.aya.tyck.env.MapLocalCtx;
import org.aya.tyck.error.CubicalError;
import org.aya.tyck.error.UnifyError;
import org.aya.tyck.error.UnifyInfo;
import org.aya.tyck.trace.Trace;
import org.aya.tyck.unify.Unifier;
import org.aya.util.Arg;
import org.aya.util.Ordering;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Problem;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public abstract sealed class CxlTycker extends StatedTycker permits ExprTycker {
  public @NotNull LocalCtx localCtx = new MapLocalCtx();

  protected CxlTycker(@NotNull Reporter reporter, Trace.@Nullable Builder traceBuilder, @NotNull TyckState state) {
    super(reporter, traceBuilder, state);
  }

  public @NotNull Unifier unifier(@NotNull SourcePos pos, @NotNull Ordering ord) {
    return unifier(pos, ord, localCtx);
  }

  /**
   * Check if <code>lower</code> is a subtype of <code>upper</code>,
   * and report a type error if it's not the case.
   *
   * @return true if well-typed.
   * @see ExprTycker#inheritFallbackUnify
   */
  public boolean unifyTyReported(@NotNull Term upper, @NotNull Term lower, Expr loc) {
    return unifyTyReported(upper, lower, loc, unification ->
      new UnifyError.Type(loc, unification, new UnifyInfo(state)));
  }

  /**
   * @param upper Expected type
   * @param lower Actual type
   * @param loc   The location of the expression
   * @param p     Callback to generate the error message
   * @return true if unified successfully, false otherwise
   */
  public boolean unifyTyReported(
    @NotNull Term upper, @NotNull Term lower, Expr loc,
    Function<UnifyInfo.Comparison, Problem> p
  ) {
    var unification = unifyTy(upper, lower, loc.sourcePos());
    if (unification != null) reporter.report(p.apply(new UnifyInfo.Comparison(
      upper.freezeHoles(state), lower.freezeHoles(state), unification
    )));
    return unification == null;
  }

  /**
   * @param loc The location of the expression
   * @param p   Callback to generate the error message
   * @return true if unified successfully, false otherwise
   */
  public boolean unifyReported(
    @NotNull Term lhs, @NotNull Term rhs, @NotNull Term ty, Expr loc,
    Function<UnifyInfo.Comparison, Problem> p
  ) {
    tracing(builder -> builder.append(
      new Trace.UnifyT(rhs.freezeHoles(state), lhs.freezeHoles(state), loc.sourcePos())));
    var unifier = unifier(loc.sourcePos(), Ordering.Eq);
    var success = unifier.compare(rhs, lhs, ty);
    // success == true ==> unification != null
    var unification = unifier.getFailure();
    if (success) reporter.report(p.apply(new UnifyInfo.Comparison(
      lhs.freezeHoles(state), rhs.freezeHoles(state), unification
    )));
    return success;
  }

  protected final void confluence(@NotNull ImmutableSeq<Restr.Side<Term>> clauses, @NotNull Expr loc, @NotNull Term type) {
    for (int i = 1; i < clauses.size(); i++) {
      var lhs = clauses.get(i);
      for (int j = 0; j < i; j++) {
        var rhs = clauses.get(j);
        CofThy.conv(lhs.cof().and(rhs.cof()), new Subst(), subst -> boundary(loc, lhs.u(), rhs.u(), type, subst));
      }
    }
  }

  protected final ExprTycker.TermResult checkBoundaries(Expr expr, PathTerm path, Subst subst, Term lambda) {
    var applied = path.applyDimsTo(lambda);
    return localCtx.withIntervals(path.params().view(), () -> {
      var happy = switch (path.partial()) {
        case Partial.Const<Term> sad -> boundary(expr, applied, sad.u(), path.type(), subst);
        case Partial.Split<Term> hap -> hap.clauses().allMatch(c ->
          CofThy.conv(c.cof(), subst, s -> boundary(expr, applied, c.u(), path.type(), s)));
      };
      return happy ? new ExprTycker.TermResult(new PLamTerm(path.params(), applied), path)
        : new ExprTycker.TermResult(ErrorTerm.unexpected(expr), path);
    });
  }

  private boolean boundary(@NotNull Expr loc, @NotNull Term lhs, @NotNull Term rhs, @NotNull Term type, Subst subst) {
    var l = whnf(lhs.subst(subst));
    var r = whnf(rhs.subst(subst));
    var t = whnf(type.subst(subst));
    return unifyReported(l, r, t, loc, comparison ->
      new CubicalError.BoundaryDisagree(loc, comparison, new UnifyInfo(state)));
  }

  /** @return null if unified successfully, otherwise a frozen data */
  protected final Unifier.FailureData unifyTy(@NotNull Term upper, @NotNull Term lower, @NotNull SourcePos pos) {
    tracing(builder -> builder.append(
      new Trace.UnifyT(lower.freezeHoles(state), upper.freezeHoles(state), pos)));
    var unifier = unifier(pos, Ordering.Lt);
    if (!unifier.compare(lower, upper, null)) return unifier.getFailure();
    else return null;
  }

  //region Term mocking
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
  //endregion
}
