// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.tycker;

import org.aya.syntax.concrete.Expr;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.xtt.DimTerm;
import org.aya.syntax.core.term.xtt.EqTerm;
import org.aya.tyck.error.UnifyError;
import org.aya.tyck.error.UnifyInfo;
import org.aya.unify.TermComparator;
import org.aya.util.Ordering;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public interface Unifiable extends Problematic, Stateful {
  @NotNull TermComparator unifier(@NotNull SourcePos pos, @NotNull Ordering order);

  /**
   * Check whether {@param lower} is a subtype of {@param upper} if {@param ordering} is {@link Ordering#Lt}.
   *
   * @param ord by default should be {@link Ordering#Lt}, or {@link Ordering#Eq}
   * @return failure data, null if success
   */
  default @Nullable TermComparator.FailureData unifyTerm(
    @NotNull Term upper, @NotNull Term lower, @Nullable Term type,
    @NotNull SourcePos pos, Ordering ord
  ) {
    var unifier = unifier(pos, ord);
    var result = unifier.compare(lower, upper, type);
    if (!result) return unifier.getFailure();
    return null;
  }

  /**
   * @param pc a problem constructor
   * @see Unifiable#unifyTerm
   */
  default boolean unifyTyReported(
    @NotNull Term upper, @NotNull Term lower, @NotNull SourcePos pos,
    @NotNull Function<UnifyInfo.Comparison, Problem> pc
  ) {
    var result = unifyTerm(upper, lower, null, pos, Ordering.Lt);
    if (result != null) fail(pc.apply(new UnifyInfo.Comparison(
      freezeHoles(lower), freezeHoles(upper), result)));
    return result == null;
  }

  default void checkBoundaries(
    EqTerm eq, Closure core, @NotNull SourcePos pos,
    @NotNull Function<UnifyInfo.Comparison, Problem> report
  ) {
    unifyTermReported(core.apply(DimTerm.I0), eq.a(), eq.appA(DimTerm.I0), pos, report);
    unifyTermReported(core.apply(DimTerm.I1), eq.b(), eq.appA(DimTerm.I1), pos, report);
  }

  default boolean unifyTermReported(
    @NotNull Term lhs, @NotNull Term rhs, @Nullable Term type, @NotNull SourcePos pos,
    @NotNull Function<UnifyInfo.Comparison, Problem> pc
  ) {
    var result = unifyTerm(lhs, rhs, type, pos, Ordering.Eq);
    if (result != null) fail(pc.apply(new UnifyInfo.Comparison(
      freezeHoles(rhs), freezeHoles(lhs), result)));
    return result == null;
  }

  default boolean unifyTyReported(@NotNull Term upper, @NotNull Term lower, @NotNull WithPos<Expr> expr) {
    return unifyTyReported(upper, lower, expr.sourcePos(),
      cp -> new UnifyError.Type(expr.data(), expr.sourcePos(), cp, new UnifyInfo(state())));
  }
}
