// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.pat;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.value.primitive.MutableBooleanValue;
import org.aya.generic.Renamer;
import org.aya.normalize.Finalizer;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.concrete.Pattern;
import org.aya.syntax.core.Jdg;
import org.aya.syntax.core.pat.Pat;
import org.aya.syntax.core.pat.PatToTerm;
import org.aya.syntax.core.pat.TypeEraser;
import org.aya.syntax.core.term.*;
import org.aya.syntax.ref.GenerateKind;
import org.aya.syntax.ref.LocalCtx;
import org.aya.syntax.ref.LocalVar;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.TyckState;
import org.aya.tyck.ctx.LocalLet;
import org.aya.tyck.error.PatternProblem;
import org.aya.tyck.pat.iter.LambdaPusheen;
import org.aya.tyck.pat.iter.PatternIterator;
import org.aya.tyck.pat.iter.SignatureIterator;
import org.aya.tyck.tycker.Problematic;
import org.aya.tyck.tycker.Stateful;
import org.aya.util.error.Panic;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public final class ClauseTycker implements Problematic, Stateful {
  private final @NotNull ExprTycker exprTycker;
  private final Finalizer.Zonk<ClauseTycker> zonker = new Finalizer.Zonk<>(this);
  public ClauseTycker(@NotNull ExprTycker exprTycker) { this.exprTycker = exprTycker; }

  public record TyckResult(@NotNull ImmutableSeq<Pat.Preclause<Term>> clauses, boolean hasLhsError) {
    public @NotNull ImmutableSeq<WithPos<Term.Matching>> wellTyped() {
      return clauses.flatMap(Pat.Preclause::lift);
    }
  }

  /**
   * @param unpiedResult the result according to the pattern tycking, the
   *                     {@link DepTypeTerm.Unpi#params} is always empty if the signature result is
   *                     {@link org.aya.tyck.pat.iter.Pusheenable.Const}
   * @param paramSubst   substitution for parameter, in the same ordeer as parameter.
   *                     See {@link PatternTycker#paramSubst}.
   * @param freePats     a free version of the patterns.
   *                     In most cases you want to use {@code clause.pats} instead
   * @param allBinds     all binders in the patterns
   * @param asSubst      substitution of the {@code as} patterns
   * @implNote If there are fewer pats than parameters, there will be some pats inserted,
   * but this will not affect {@code paramSubst}, and the inserted pat are "ignored" in tycking
   * of the body, because we check the body against to {@link #unpiedResult}.
   * Then we apply the inserted pats to the body to complete it.
   */
  public record LhsResult(
    @NotNull LocalCtx localCtx,
    @NotNull DepTypeTerm.Unpi unpiedResult,
    @NotNull ImmutableSeq<LocalVar> allBinds,
    @NotNull ImmutableSeq<Pat> freePats,
    @NotNull ImmutableSeq<Jdg> paramSubst,
    @NotNull LocalLet asSubst,
    @NotNull Pat.Preclause<Expr> clause,
    boolean hasError
  ) {
    public @NotNull LhsResult mapPats(@NotNull UnaryOperator<Pat> f) {
      return new LhsResult(localCtx, unpiedResult, allBinds,
        freePats.map(f), paramSubst, asSubst, clause, hasError);
    }

    public @NotNull SeqView<Pat> unpiPats() {
      return clause.pats().view().takeLast(unpiedResult.params().size());
    }

    /// Returns the instantiated result type of this clause
    public @NotNull Term instResult() {
      // We need not to inline this term even we did before. The [paramSubst] is already inlined.
      return unpiedResult.makePi().instTele(paramSubst.view().map(Jdg::wellTyped));
    }

    @Contract(mutates = "param2")
    public void addLocalLet(@NotNull ImmutableSeq<LocalVar> teleBinds, @NotNull ExprTycker exprTycker) {
      teleBinds.forEachWith(paramSubst, exprTycker.localLet()::put);
      exprTycker.setLocalLet(new LocalLet(exprTycker.localLet(), asSubst.subst()));
    }
  }

  public record Worker(
    @NotNull ClauseTycker parent,
    @NotNull ImmutableSeq<Param> telescope,
    @NotNull DepTypeTerm.Unpi unpi,
    @NotNull ImmutableSeq<LocalVar> teleVars,
    @NotNull ImmutableSeq<LocalVar> elims,
    @NotNull ImmutableSeq<Pattern.Clause> clauses,
    boolean isFn
  ) {
    public @NotNull TyckResult check(@NotNull SourcePos overallPos) {
      var lhs = checkAllLhs();

      if (lhs.noneMatch(r -> r.hasError)) {
        var classes = PatClassifier.classify(
          lhs.view().map(LhsResult::clause),
          telescope.view().concat(unpi.params()), parent.exprTycker, overallPos);
        if (clauses.isNotEmpty()) {
          var usages = PatClassifier.firstMatchDomination(clauses, parent, classes);
          // refinePatterns(lhs, usages, classes);
        }
      }

      return parent.checkAllRhs(teleVars, lhs.map(cl -> cl.mapPats(new TypeEraser())));
    }

    public @NotNull ImmutableSeq<LhsResult> checkAllLhs() {
      return parent.checkAllLhs(() ->
          SignatureIterator.make(telescope, unpi, teleVars, elims),
        clauses.view(), isFn);
    }

    public @NotNull TyckResult checkNoClassify() {
      return parent.checkAllRhs(teleVars, checkAllLhs());
    }
  }

  @Override public @NotNull Reporter reporter() { return exprTycker.reporter; }
  @Override public @NotNull TyckState state() { return exprTycker.state; }

  // region tycking

  public @NotNull ImmutableSeq<LhsResult> checkAllLhs(
    @NotNull Supplier<SignatureIterator> sigIterFactory,
    @NotNull SeqView<Pattern.Clause> clauses, boolean isFn
  ) {
    return clauses.map(c -> checkLhs(sigIterFactory.get(), c, isFn)).toImmutableSeq();
  }

  public @NotNull TyckResult checkAllRhs(
    @NotNull ImmutableSeq<LocalVar> vars,
    @NotNull ImmutableSeq<LhsResult> lhsResults
  ) {
    var lhsError = lhsResults.anyMatch(LhsResult::hasError);
    var rhsResult = lhsResults.map(x -> checkRhs(vars, x));

    // inline terms in rhsResult
    rhsResult = rhsResult.map(preclause -> new Pat.Preclause<>(
      preclause.sourcePos(),
      preclause.pats().map(p -> p.descentTerm(zonker::zonk)),
      preclause.bindCount(), preclause.expr()
    ));

    return new TyckResult(rhsResult, lhsError);
  }

  public @NotNull LhsResult checkLhs(
    @NotNull SignatureIterator sigIter,
    @NotNull Pattern.Clause clause,
    boolean isFn
  ) {
    var tycker = newPatternTycker(sigIter, sigIter.elims != null);
    try (var _ = exprTycker.subscope()) {
      // If a pattern occurs in elimination environment, then we check if it contains absurd pattern.
      // If it is not the case, the pattern must be accompanied by a body.
      if (isFn && !clause.patterns.anyMatch(p -> hasAbsurdity(p.term().data())) && clause.expr.isEmpty()) {
        clause.hasError = true;
        exprTycker.fail(new PatternProblem.InvalidEmptyBody(clause));
      }

      var patIter = new PatternIterator(clause.patterns, clause.expr.isDefined() ? new LambdaPusheen(clause.expr.get()) : PatternIterator.DUMMY);
      var patResult = tycker.tyck(patIter, null);
      var ctx = exprTycker.localCtx();   // No need to copy the context here

      clause.hasError |= patResult.hasError();
      patResult = inline(patResult, ctx);
      clause.patterns.view().map(it -> it.term().data()).forEach(TermInPatInline::apply);

      // It is safe to replace ctx:
      // * telescope are well-typed and no Meta
      // * PatternTycker doesn't introduce any Meta term
      ctx = ctx.map(new TermInline());
      var patWithTypeBound = Pat.collectVariables(patResult.wellTyped().view());

      var unpiBody = sigIter.unpiBody();
      // fill missing patterns
      var missingPats = unpiBody.params().mapIndexed((idx, x) ->
        // It would be nice if we have a SourcePos here
        new Pat.Bind(new LocalVar("unpi" + idx, SourcePos.NONE, GenerateKind.Basic.Tyck), x.type()));

      var allBinds = patWithTypeBound.component1().toImmutableSeq();
      var newClause = new Pat.Preclause<>(clause.sourcePos, patWithTypeBound.component2(),
        allBinds.size(), patIter.exprBody());
      return new LhsResult(ctx, sigIter.unpiBody(), allBinds,
        patResult.wellTyped().appendedAll(missingPats),
        patResult.paramSubst(), patResult.asSubst(), newClause, patResult.hasError());
    }
  }

  /**
   * Tyck the rhs of some clause.
   *
   * @param result the tyck result of the corresponding patterns
   */
  private @NotNull Pat.Preclause<Term> checkRhs(
    @NotNull ImmutableSeq<LocalVar> teleBinds,
    @NotNull LhsResult result
  ) {
    try (var _ = exprTycker.subscope()) {
      var clause = result.clause;
      var bodyExpr = clause.expr();
      Term wellBody;
      var bindCount = 0;
      if (bodyExpr == null) wellBody = null;
      else if (result.hasError) {
        // In case the patterns are malformed, do not check the body
        // as we bind local variables in the pattern checker,
        // and in case the patterns are malformed, some bindings may
        // not be added to the localCtx of tycker, causing assertion errors
        wellBody = new ErrorTerm(result.clause.expr().data());
      } else {
        // the localCtx will be restored after exiting [subscoped]e
        exprTycker.setLocalCtx(result.localCtx);
        result.addLocalLet(teleBinds, exprTycker);
        // now exprTycker has all substitutions that PatternTycker introduced.
        wellBody = exprTycker.inherit(bodyExpr, result.instResult()).wellTyped();
        exprTycker.solveMetas();
        wellBody = zonker.zonk(wellBody);

        // eta body with inserted patterns
        wellBody = AppTerm.make(wellBody, result.unpiPats().map(PatToTerm::visit));

        // bind all pat bindings
        var patBindTele = Pat.collectVariables(result.clause.pats().view()).component1();
        bindCount = patBindTele.size();
        wellBody = wellBody.bindTele(patBindTele.view());
      }

      return new Pat.Preclause<>(clause.sourcePos(), clause.pats(), bindCount,
        wellBody == null ? null : WithPos.dummy(wellBody));
    }
  }

  // endregion tycking

  // region util

  private @NotNull PatternTycker newPatternTycker(@NotNull SignatureIterator sigIter, boolean hasElim) {
    return new PatternTycker(exprTycker, sigIter, new LocalLet(), !hasElim,
      new Renamer());
  }

  private static boolean hasAbsurdity(@NotNull Pattern term) {
    return hasAbsurdity(term, MutableBooleanValue.create());
  }

  private static boolean hasAbsurdity(@NotNull Pattern term, @NotNull MutableBooleanValue b) {
    if (term == Pattern.Absurd.INSTANCE) b.set(true);
    else term.forEach((_, p) -> b.set(b.get() || hasAbsurdity(p, b)));
    return b.get();
  }

  // endregion util

  // region post tycking

  private static final class TermInline implements UnaryOperator<Term> {
    @Override public @NotNull Term apply(@NotNull Term term) {
      if (term instanceof MetaPatTerm metaPat) {
        var isEmpty = metaPat.meta().solution().isEmpty();
        if (isEmpty) throw new Panic("Unable to inline " + metaPat.toDoc(AyaPrettierOptions.debug()));
        // the solution may contain other MetaPatTerm
        return metaPat.inline(this);
      } else {
        return term.descent(this);
      }
    }
  }

  /**
   * Inline terms which in pattern
   */
  private static final class TermInPatInline {
    public static void apply(@NotNull Pattern pat) {
      var typeRef = switch (pat) {
        case Pattern.Bind bind -> bind.type();
        case Pattern.As as -> as.type();
        default -> null;
      };

      if (typeRef != null) typeRef.update(it -> it == null ? null :
        it.descent(new TermInline()));

      pat.forEach((_, p) -> apply(p));
    }
  }

  private static @NotNull Jdg inlineTerm(@NotNull Jdg r) {
    return r.map(new TermInline());
  }

  /**
   * Inline terms in {@param result}, please do this after inline all patterns
   */
  private static @NotNull PatternTycker.TyckResult inline(@NotNull PatternTycker.TyckResult result, @NotNull LocalCtx ctx) {
    // inline {Pat.Meta} before inline {MetaPatTerm}s
    var wellTyped = result.wellTyped().map(x ->
      x.inline(ctx::put).descentTerm(new TermInline()));
    // so that {MetaPatTerm}s can be inlined safely
    var paramSubst = result.paramSubst().map(ClauseTycker::inlineTerm);

    // map in place 😱😱😱😱
    result.asSubst().subst().replaceAll((_, t) -> inlineTerm(t));

    return new PatternTycker.TyckResult(wellTyped, paramSubst, result.asSubst(), result.hasError());
  }

  // endregion post tycking
}
