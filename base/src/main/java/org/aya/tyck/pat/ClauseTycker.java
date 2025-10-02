// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.pat;

import kala.collection.Seq;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.primitive.ImmutableIntArray;
import kala.collection.immutable.primitive.ImmutableIntSeq;
import kala.collection.mutable.MutableSeq;
import kala.value.primitive.MutableBooleanValue;
import org.aya.generic.Renamer;
import org.aya.normalize.Finalizer;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.concrete.Pattern;
import org.aya.syntax.core.Jdg;
import org.aya.syntax.core.annotation.Closed;
import org.aya.syntax.core.def.FnClauseBody;
import org.aya.syntax.core.pat.Pat;
import org.aya.syntax.core.pat.PatToTerm;
import org.aya.syntax.core.term.*;
import org.aya.syntax.ref.LocalCtx;
import org.aya.syntax.ref.LocalVar;
import org.aya.syntax.telescope.AbstractTele;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.TyckState;
import org.aya.tyck.ctx.LocalLet;
import org.aya.tyck.error.PatternProblem;
import org.aya.tyck.pat.iter.LambdaPusheen;
import org.aya.tyck.pat.iter.PatternIterator;
import org.aya.tyck.pat.iter.SignatureIterator;
import org.aya.tyck.tycker.Problematic;
import org.aya.tyck.tycker.Stateful;
import org.aya.util.Panic;
import org.aya.util.position.SourceNode;
import org.aya.util.position.SourcePos;
import org.aya.util.position.WithPos;
import org.aya.util.reporter.Reporter;
import org.aya.util.tyck.pat.PatClass;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
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
    /// @return null if there is no absurd pattern
    public @Nullable ImmutableIntSeq absurdPrefixCount() {
      var ints = new int[clauses.size()];
      var count = 0;
      for (int i = 0; i < clauses.size(); i++) {
        var clause = clauses.get(i);
        if (clause.expr() == null) count++;
        ints[i] = count;
      }
      if (count == 0) return null;
      return ImmutableIntArray.Unsafe.wrap(ints);
    }
  }

  /// @param result      the result according to the pattern tycking, the
  ///                    [#params] is always empty if the signature result is
  ///                    [org.aya.tyck.pat.iter.Pusheenable.Const].
  ///                    The result is always [Closed] under [#localCtx]
  /// @param paramSubst  substitution for parameter, in the same ordeer as parameter. [Closed] under [#localCtx].
  ///                    Only used by ExprTycker, see [#dumpLocalLetTo]
  /// @param tyckedPats  tycked, free version of the patterns, [Closed] under [#localCtx()]
  /// @param missingPats patterns that makes [#freePats()] agree with the pusheen signature. Be careful that [#missingPats]
  ///                    is not necessary to be [Pat.Bind], see [Worker#refinePattern].
  /// @param asSubst     substitution of the `as` patterns, [Closed] under [#localCtx]
  /// @implNote TL;DR: `freePats.dropLast(unpiParamSize)` is compatible with [#localCtx], [#paramSubst], [#result] and [#body]
  /// If there are fewer pats than parameters, there will be some pats inserted,
  /// but this will not affect `paramSubst`, and the inserted pat are "ignored" in tycking
  /// of the body, because we check the body against to [#result].
  /// Then we apply the inserted pats to the body to complete it.
  public record LhsResult(
    @NotNull LocalCtx localCtx,
    @NotNull @Closed Term result,
    @NotNull ImmutableSeq<@Closed Pat> tyckedPats,
    @NotNull ImmutableSeq<@Closed Pat> missingPats,
    @Override @NotNull SourcePos sourcePos,
    @Nullable WithPos<Expr> body,
    @NotNull ImmutableSeq<@Closed Jdg> paramSubst,
    @NotNull LocalLet asSubst,
    boolean hasError
  ) implements SourceNode {
    public @NotNull ImmutableSeq<@Closed Pat> freePats() {
      return tyckedPats.appendedAll(missingPats);
    }

    @Deprecated
    public int unpiParamSize() {
      return missingPats.size();
    }

    @Contract(mutates = "param2")
    public void dumpLocalLetTo(@NotNull ImmutableSeq<LocalVar> teleBinds, @NotNull ExprTycker exprTycker) {
      teleBinds.forEachWith(paramSubst, exprTycker.localLet()::put);
      exprTycker.setLocalLet(exprTycker.localLet().derive(asSubst.let()));
    }
  }

  public record WorkerResult(FnClauseBody wellTyped, boolean hasLhsError) { }
  public record Worker(
    @NotNull ClauseTycker parent,
    @NotNull ImmutableSeq<Param> telescope,
    @NotNull DepTypeTerm.Unpi unpi,
    @NotNull ImmutableSeq<LocalVar> teleVars,
    @NotNull ImmutableSeq<LocalVar> elims,
    @NotNull ImmutableSeq<Pattern.Clause> clauses
  ) {
    public @NotNull WorkerResult check(@NotNull SourcePos overallPos) {
      var lhs = checkAllLhs();

      ImmutableSeq<PatClass.Seq<Term, Pat>> classes;
      var hasError = lhs.anyMatch(LhsResult::hasError);
      if (!hasError) {
        classes = PatClassifier.classify(
          lhs.view().map(LhsResult::freePats),
          telescope.view().concat(unpi.params()), parent.exprTycker, overallPos);
        if (clauses.isNotEmpty()) {
          var usages = PatClassifier.firstMatchDomination(clauses, parent, classes);
          for (int i = 0; i < usages.size(); i++) {
            if (clauses.get(i).expr.isEmpty()) continue;
            var currentClasses = usages.get(i);
            if (currentClasses.sizeEquals(1)) {
              var newLhs = refinePattern(lhs.get(i), currentClasses.get(0));
              if (newLhs != null) lhs.set(i, newLhs);
            }
          }
        }
      } else {
        classes = null;
      }

      var rhs = parent.checkAllRhs(teleVars, lhs, hasError);
      var wellTyped = new FnClauseBody(rhs.wellTyped());
      if (classes != null) {
        var absurds = rhs.absurdPrefixCount();
        wellTyped.classes = classes.map(cl -> cl.ignoreAbsurd(absurds));
      }
      return new WorkerResult(wellTyped, hasError);
    }

    /// When we realize (in first-match only) that a clause is only reachable for a single leaf in the case tree,
    /// we try to specialize the patterns according to the case tree leaf. For example,
    /// ```
    /// f zero = body1
    /// f x = body2
    ///```
    /// The `x` in the second case is only reachable for input `suc y`,
    /// and we can realize this by inspecting the result of [PatClassifier#firstMatchDomination].
    /// So, we can replace `x` with `suc y` to help computing the result type.
    /// A more realistic motivating example can be found
    /// [here](https://twitter.com/zornsllama/status/1465435870861926400).
    ///
    /// However, we cannot just simply replace the patterns -- the localCtx obtained by checking the patterns,
    /// the result type [LhsResult#result], the types in the patterns, and [LhsResult#paramSubst],
    /// all of these need to be changed accordingly.
    /// This method performs these changes.
    private @Nullable LhsResult refinePattern(LhsResult curLhs, PatClass.Seq<Term, Pat> curCls) {
      var lets = new PatBinder().apply(curLhs.freePats(), curCls.term());
      if (lets.let().let().allMatch((_, j) -> j.wellTyped() instanceof FreeTerm))
        return null;
      var sibling = Objects.requireNonNull(curLhs.localCtx.parent()).derive();
      var newPatterns = curCls.pat().map(pat -> pat.descentTerm(lets));
      newPatterns.forEach(pat -> pat.consumeBindings(sibling::put));
      curLhs.asSubst.let().replaceAll((_, t) -> t.map(lets));
      var paramSubst = curLhs.paramSubst.map(jdg -> jdg.map(lets));
      lets.let().let().forEach(curLhs.asSubst::put);
      return new LhsResult(
        sibling, lets.apply(curLhs.result),
        newPatterns.dropLast(curLhs.unpiParamSize()),
        // Well... It is possible if the type has only one constructor, like Unit
        newPatterns.takeLast(curLhs.unpiParamSize()),
        curLhs.sourcePos, curLhs.body, paramSubst, curLhs.asSubst, curLhs.hasError);
    }

    public @NotNull MutableSeq<LhsResult> checkAllLhs() {
      return parent.checkAllLhs(() ->
          SignatureIterator.make(telescope, unpi, teleVars, elims),
        clauses.view(), unpi.params().size());
    }

    public @NotNull TyckResult checkNoClassify() {
      var lhsResults = checkAllLhs();
      return parent.checkAllRhs(teleVars, lhsResults, lhsResults.anyMatch(LhsResult::hasError));
    }
  }

  @Override public @NotNull Reporter reporter() { return exprTycker.reporter; }
  @Override public @NotNull TyckState state() { return exprTycker.state; }

  // region tycking

  public @NotNull MutableSeq<LhsResult> checkAllLhs(
    @NotNull Supplier<SignatureIterator> sigIterFactory,
    @NotNull SeqView<Pattern.Clause> clauses, int userUnpiSize
  ) {
    return MutableSeq.from(clauses.map(c ->
      checkLhs(sigIterFactory.get(), c, true, userUnpiSize)));
  }

  public @NotNull TyckResult checkAllRhs(
    @NotNull ImmutableSeq<LocalVar> vars,
    @NotNull Seq<LhsResult> lhsResults,
    boolean lhsError
  ) {
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
    boolean isFn, int userUnpiSize
  ) {
    var tycker = newPatternTycker(sigIter, sigIter.elims != null);
    // PatClassifier relies on the subscope behavior happened here
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
      clause.patterns.forEach(it -> TermInPatInline.apply(it.term().data()));

      // It is safe to replace ctx:
      // * telescope are well-typed and no Meta
      // * PatternTycker doesn't introduce any Meta term
      ctx = ctx.map(new TermInline());

      // fill missing patterns
      // This is not a typo of "repl"
      @Closed var instRepi = sigIter.unpiBody().makePi()
        // safe to inst, as paramSubst is closed
        .instTele(patResult.paramSubst().view().map(Jdg::wellTyped));
      // unpi [instRepi] **at most** [userUnpiSize]
      var instUnpiParam = DepTypeTerm.unpiUnsafe(instRepi, userUnpiSize);
      // be careful that [freeParam.size() <= userUnpiSize], `==` is not always true.
      var freeParam = AbstractTele.enrich(new AbstractTele.Locns(instUnpiParam.params(), instUnpiParam.body()));
      var missingPats = freeParam.map((x) -> new Pat.Bind(x.ref(), x.type()));

      // ImmutableSeq<@Closed Pat> wellTypedPats = patResult.wellTyped().appendedAll(missingPats);
      return new LhsResult(ctx, instRepi,
        patResult.wellTyped(), ImmutableSeq.narrow(missingPats),
        clause.sourcePos, patIter.exprBody(),
        patResult.paramSubst(), patResult.asSubst(), patResult.hasError());
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
      var bodyExpr = result.body;
      Term wellBody;
      var bindCount = 0;
      var pats = result.freePats();
      if (bodyExpr == null) wellBody = null;
      else if (result.hasError) {
        // In case the patterns are malformed, do not check the body
        // as we bind local variables in the pattern checker,
        // and in case the patterns are malformed, some bindings may
        // not be added to the localCtx of tycker, causing assertion errors
        wellBody = new ErrorTerm(bodyExpr.data());
      } else {
        // the localCtx will be restored after exiting [subscoped]
        exprTycker.setLocalCtx(result.localCtx);
        result.dumpLocalLetTo(teleBinds, exprTycker);
        // now exprTycker has all substitutions that PatternTycker introduced.
        wellBody = exprTycker.inherit(bodyExpr, result.result()).wellTyped();
        exprTycker.solveMetas();
        wellBody = zonker.zonk(wellBody);

        // bind all pat bindings
        var patWithTypeBound = Pat.collectVariables(result.freePats().view());
        pats = patWithTypeBound.component2();
        var patBindTele = patWithTypeBound.component1();

        bindCount = patBindTele.size();

        // eta body with inserted patterns
        wellBody = AppTerm.make(wellBody, result.missingPats().view().map(PatToTerm::visit));
        wellBody = wellBody.bindTele(patBindTele.view());
      }

      return new Pat.Preclause<>(result.sourcePos, pats, bindCount,
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
        case Pattern.Bind bind -> bind.theCoreType();
        case Pattern.As as -> as.theCoreType();
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
    result.asSubst().let().replaceAll((_, t) -> inlineTerm(t));

    return new PatternTycker.TyckResult(wellTyped, paramSubst, result.asSubst(), result.hasError());
  }

  // endregion post tycking
}
