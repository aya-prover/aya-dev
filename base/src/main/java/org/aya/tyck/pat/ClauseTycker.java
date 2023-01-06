// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.pat;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import kala.value.MutableValue;
import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.concrete.visitor.PatternConsumer;
import org.aya.core.def.Def;
import org.aya.core.pat.Pat;
import org.aya.core.term.ErrorTerm;
import org.aya.core.term.MetaPatTerm;
import org.aya.core.term.Term;
import org.aya.core.visitor.EndoTerm;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.env.LocalCtx;
import org.aya.tyck.trace.Trace;
import org.aya.util.Arg;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 * TODO: interface?
 */
public final class ClauseTycker {
  public static final EndoTerm META_PAT_INLINER = new EndoTerm() {
    @Override public @NotNull Term post(@NotNull Term term) {
      return term instanceof MetaPatTerm metaPat ? metaPat.inline(this) : term;
    }
  };

  public record PatResult(
    @NotNull Term result,
    @NotNull ImmutableSeq<Pat.Preclause<Term>> clauses,
    @NotNull ImmutableSeq<Term.Matching> matchings,
    boolean hasLhsError
  ) {
  }

  public static @NotNull PatResult elabClausesDirectly(
    @NotNull ExprTycker exprTycker,
    @NotNull ImmutableSeq<Pattern.@NotNull Clause> clauses,
    @NotNull Def.Signature<?> signature
  ) {
    return checkAllRhs(exprTycker, checkAllLhs(exprTycker, clauses, signature), signature.result());
  }

  public static @NotNull PatResult elabClausesClassified(
    @NotNull ExprTycker exprTycker,
    @NotNull ImmutableSeq<Pattern.@NotNull Clause> clauses,
    @NotNull Def.Signature<?> signature,
    @NotNull SourcePos overallPos
  ) {
    var lhsResults = checkAllLhs(exprTycker, clauses, signature);
    if (!lhsResults.hasError()) {
      var classes = PatClassifier.classify(lhsResults.lhsResult().view().map(LhsResult::preclause),
        signature.param(), exprTycker, overallPos);
      if (clauses.isNotEmpty()) {
        var usages = PatClassifier.firstMatchDomination(clauses, exprTycker.reporter, classes);
        // refinePatterns(lhsResults, usages, classes);
      }
    }

    return checkAllRhs(exprTycker, lhsResults, signature.result());
  }

  private static @NotNull ClauseTycker.AllLhsResult checkAllLhs(
    @NotNull ExprTycker exprTycker,
    @NotNull ImmutableSeq<Pattern.@NotNull Clause> clauses,
    @NotNull Def.Signature<?> signature
  ) {
    // TODO[isType]: revise this
    // https://github.com/agda/agda/blob/66d22577abe9ac67649c6e662c91d8593d1bf86c/src/full/Agda/TypeChecking/Rules/LHS.hs#L2099-L2136
    var inProp = exprTycker.ctx.with(() ->
      exprTycker.isPropType(signature.result()), signature.param().view());
    return new AllLhsResult(clauses.mapIndexed((index, clause) -> exprTycker.traced(
      () -> new Trace.LabelT(clause.sourcePos, "lhs of clause " + (1 + index)),
      () -> checkLhs(exprTycker, clause, signature, inProp, true))));
  }

  private static @NotNull PatResult checkAllRhs(
    @NotNull ExprTycker exprTycker,
    @NotNull AllLhsResult lhsResult,
    @NotNull Term result
  ) {
    var clauses = lhsResult.lhsResult();
    var res = clauses.mapIndexed((index, lhs) -> exprTycker.traced(
      () -> new Trace.LabelT(lhs.preclause.sourcePos(), "rhs of clause " + (1 + index)),
      () -> checkRhs(exprTycker, lhs)));
    var preclauses = res.map(c -> new Pat.Preclause<>(
      c.sourcePos(), c.patterns().map(p -> p.descent(x -> x.zonk(exprTycker))),
      c.expr().map(exprTycker::zonk)));
    return new PatResult(exprTycker.zonk(result), preclauses,
      preclauses.flatMap(Pat.Preclause::lift), lhsResult.hasError());
  }

  public record AllLhsResult(
    @NotNull ImmutableSeq<LhsResult> lhsResult,
    boolean hasError
  ) {
    public AllLhsResult(@NotNull ImmutableSeq<LhsResult> lhsResults) {
      this(lhsResults, lhsResults.anyMatch(LhsResult::hasError));
    }
  }

  /**
   * @param bodySubst we do need to replace them with the corresponding patterns,
   *                  but patterns are terms (they are already well-typed if not {@param hasError})
   * @param hasError  if there is an error in the patterns
   */
  public record LhsResult(
    @NotNull LocalCtx gamma,
    @NotNull Term type,
    @NotNull TypedSubst bodySubst,
    boolean hasError,
    @NotNull Pat.Preclause<Expr> preclause
  ) {
  }

  /**
   * @param isElim whether this checking is used for elimination (rather than data declaration)
   */
  public static @NotNull LhsResult checkLhs(
    @NotNull ExprTycker exprTycker,
    @NotNull Pattern.Clause match,
    @NotNull Def.Signature<?> signature,
    boolean inProp,
    boolean isElim
  ) {
    var patTycker = new PatternTycker(exprTycker, signature, match.patterns.view());
    return exprTycker.subscoped(() -> {
      // If a pattern occurs in elimination environment, then we check if it contains absurd pattern.
      // If it is not the case, the pattern must be accompanied by a body.
      if (isElim && !match.patterns.anyMatch(p -> hasAbsurdity(p.term())) && match.expr.isEmpty()) {
        match.hasError = true;
        exprTycker.reporter.report(new PatternProblem.InvalidEmptyBody(match));
      }
      var step0 = patTycker.tyck(null, match.expr.getOrNull(), inProp);

      match.hasError |= patTycker.hasError();

      var patterns = step0.wellTyped().map(p -> p.descent(x -> x.inline(exprTycker.ctx)));
      // inline after inline patterns
      inlineTypedSubst(patTycker.bodySubst);
      var type = inlineTerm(step0.codomain());
      exprTycker.ctx.modifyMyTerms(META_PAT_INLINER);
      var consumer = new PatternConsumer() {
        @Override public void pre(@NotNull Pattern pat) {
          var typeRef = switch (pat) {
            case Pattern.Bind bind -> bind.type();
            case Pattern.As as -> as.type();
            default -> MutableValue.<Term>create();
          };

          typeRef.update(t -> t == null ? null : inlineTerm(t));

          PatternConsumer.super.pre(pat);
        }
      };
      match.patterns.view().map(Arg::term).forEach(consumer::accept);

      return new LhsResult(exprTycker.ctx, type, patTycker.bodySubst, patTycker.hasError(),
        new Pat.Preclause<>(match.sourcePos, patterns, Option.ofNullable(step0.newBody())));
    });
  }

  private static Pat.Preclause<Term> checkRhs(@NotNull ExprTycker exprTycker, @NotNull LhsResult lhsResult) {
    return exprTycker.subscoped(() -> {
      exprTycker.ctx = lhsResult.gamma;
      var term = exprTycker.subscoped(() -> {
        // We `addDirectly` to `definitionEqualities`.
        // This means terms in `definitionEqualities` won't be substituted by `lhsResult.bodySubst`
        exprTycker.definitionEqualities.addDirectly(lhsResult.bodySubst());
        return lhsResult.preclause.expr().map(e -> lhsResult.hasError
          // In case the patterns are malformed, do not check the body
          // as we bind local variables in the pattern checker,
          // and in case the patterns are malformed, some bindings may
          // not be added to the localCtx of tycker, causing assertion errors
          ? new ErrorTerm(e, false)
          : exprTycker.check(e, lhsResult.type).wellTyped());
      });

      return new Pat.Preclause<>(lhsResult.preclause.sourcePos(), lhsResult.preclause.patterns(), term);
    });
  }

  /// region Helper

  /**
   * check if absurdity is contained in pattern
   */
  private static boolean hasAbsurdity(@NotNull Pattern pattern) {
    return switch (pattern) {
      case Pattern.Absurd ignored -> true;
      case Pattern.As as -> hasAbsurdity(as.pattern());
      case Pattern.BinOpSeq binOpSeq -> binOpSeq.seq().anyMatch(p -> hasAbsurdity(p.term()));
      case Pattern.Ctor ctor -> ctor.params().anyMatch(p -> hasAbsurdity(p.term()));
      case Pattern.List list -> list.elements().anyMatch(ClauseTycker::hasAbsurdity);
      case Pattern.Tuple tuple -> tuple.patterns().anyMatch(p -> hasAbsurdity(p.term()));
      default -> false;
    };
  }

  public static @NotNull Term inlineTerm(@NotNull Term term) {
    return META_PAT_INLINER.apply(term);
  }

  public static @NotNull TypedSubst inlineTypedSubst(@NotNull TypedSubst tySubst) {
    tySubst.subst().map().replaceAll((var, term) -> inlineTerm(term));
    tySubst.type().replaceAll((var, term) -> inlineTerm(term));

    return tySubst;
  }

  /// endregion
}
