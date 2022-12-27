// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.tycker;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableArrayList;
import org.aya.concrete.Expr;
import org.aya.core.term.*;
import org.aya.core.visitor.Subst;
import org.aya.generic.AyaDocile;
import org.aya.guest0x0.cubical.CofThy;
import org.aya.guest0x0.cubical.Partial;
import org.aya.guest0x0.cubical.Restr;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.Result;
import org.aya.tyck.env.LocalCtx;
import org.aya.tyck.error.CubicalError;
import org.aya.tyck.error.UnifyError;
import org.aya.tyck.error.UnifyInfo;
import org.aya.tyck.trace.Trace;
import org.aya.tyck.unify.Unifier;
import org.aya.util.Ordering;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Problem;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * This is the fourth base-base class of a tycker.
 * It has no new members, and supports some unification and cubical boundary functions.
 *
 * @author ice1000
 * @see #unifier(SourcePos, Ordering)
 * @see #unifyTy(Term, Term, SourcePos)
 * @see #unifyReported(Term, Term, Term, Expr, Function)
 * @see #unifyTyReported
 * @see #confluence
 * @see #checkBoundaries
 * @see #inheritFallbackUnify
 */
public sealed abstract class UnifiedTycker extends MockedTycker permits PropTycker {

  CoercionAgent coercionAgent;
  protected UnifiedTycker(@NotNull Reporter reporter, Trace.@Nullable Builder traceBuilder, @NotNull TyckState state) {
    super(reporter, traceBuilder, state);
    coercionAgent = new CoercionAgent(this, CoercionAgent.DEFAULT_COERCERS.view());
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
      lower.freezeHoles(state), upper.freezeHoles(state), unification
    )));
    return unification == null;
  }

  /**
   * Check if <code>lower</code> is a subtype of <code>upper</code>,
   * and try to insert implicit arguments to fulfill this goal (if possible).
   *
   * @return the term and type after insertion
   * @see #unifyTyReported(Term, Term, Expr)
   */
  protected final Result inheritFallbackUnify(@NotNull Term upper, @NotNull Result result, Expr loc) {
    return coercionAgent.coerceWithFallback(result, upper, loc);
  }

  protected final @NotNull Result error(@NotNull AyaDocile expr, @NotNull Term term) {
    return new Result.Default(new ErrorTerm(expr), term);
  }

  /**
   * @param pos
   * @param ctx
   * @param p   Callback to generate the error message
   * @return true if unified successfully, false otherwise
   */
  public boolean unifyReported(
    @NotNull Term lhs, @NotNull Term rhs, @NotNull Term ty, @NotNull SourcePos pos, @NotNull LocalCtx ctx,
    Function<UnifyInfo.Comparison, Problem> p
  ) {
    tracing(builder -> builder.append(
      new Trace.UnifyT(lhs.freezeHoles(state), rhs.freezeHoles(state), pos)));
    var unifier = unifier(pos, Ordering.Eq, ctx);
    var success = unifier.compare(lhs, rhs, ty);
    // success == true ==> unification != null
    var unification = success ? null : unifier.getFailure();
    if (!success) reporter.report(p.apply(new UnifyInfo.Comparison(
      lhs.freezeHoles(state), rhs.freezeHoles(state), unification
    )));
    return success;
  }

  public boolean unifyReported(
    @NotNull Term lhs, @NotNull Term rhs, @NotNull Term ty, Expr loc,
    Function<UnifyInfo.Comparison, Problem> p
  ) {
    return unifyReported(lhs, rhs, ty, loc.sourcePos(), localCtx, p);
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

  protected final Result.Default checkBoundaries(Expr expr, PathTerm path, Subst subst, Term lambda) {
    var applied = path.applyDimsTo(lambda);
    return localCtx.withIntervals(path.params().view(), () -> {
      var happy = switch (path.partial()) {
        case Partial.Const<Term> sad -> boundary(expr, applied, sad.u(), path.type(), subst);
        case Partial.Split<Term> hap -> hap.clauses().allMatch(c ->
          CofThy.conv(c.cof(), subst, s -> boundary(expr, applied, c.u(), path.type(), s)));
      };
      return happy ? new Result.Default(new PLamTerm(path.params(), applied), path)
        : new Result.Default(ErrorTerm.unexpected(expr), path);
    });
  }

  private boolean boundary(@NotNull Expr loc, @NotNull Term lhs, @NotNull Term rhs, @NotNull Term type, Subst subst) {
    return unifyReported(lhs.subst(subst), rhs.subst(subst), type.subst(subst),
      loc, comparison -> new CubicalError.BoundaryDisagree(loc, comparison, new UnifyInfo(state)));
  }

  /** @return null if unified successfully, otherwise a frozen data */
  protected final Unifier.FailureData unifyTy(@NotNull Term upper, @NotNull Term lower, @NotNull SourcePos pos) {
    tracing(builder -> builder.append(
      new Trace.UnifyT(lower.freezeHoles(state), upper.freezeHoles(state), pos)));
    var unifier = unifier(pos, Ordering.Lt);
    if (!unifier.compare(lower, upper, null)) return unifier.getFailure();
    else return null;
  }
}
