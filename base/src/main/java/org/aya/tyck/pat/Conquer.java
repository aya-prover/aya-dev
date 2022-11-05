// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.pat;

import kala.collection.immutable.ImmutableSeq;
import kala.tuple.Tuple;
import org.aya.core.def.Def;
import org.aya.core.pat.Pat;
import org.aya.core.pat.PatMatcher;
import org.aya.core.pat.PatToTerm;
import org.aya.core.term.ErrorTerm;
import org.aya.core.term.MetaTerm;
import org.aya.core.term.Term;
import org.aya.core.visitor.Expander;
import org.aya.core.visitor.Subst;
import org.aya.generic.Arg;
import org.aya.generic.util.NormalizeMode;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.env.LocalCtx;
import org.aya.tyck.env.MapLocalCtx;
import org.aya.util.Ordering;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

/**
 * The name is short for "condition checker"
 *
 * @author ice1000
 */
public record Conquer(
  @NotNull ImmutableSeq<Term.Matching> matchings,
  @NotNull SourcePos sourcePos,
  @NotNull Def.Signature signature,
  boolean orderIndependent,
  @NotNull ExprTycker tycker
) {
  public static void against(
    @NotNull ImmutableSeq<Term.Matching> matchings, boolean orderIndependent,
    @NotNull ExprTycker tycker, @NotNull SourcePos pos, @NotNull Def.Signature signature
  ) {
    var conquer = new Conquer(matchings, pos, signature, orderIndependent, tycker);
    for (int i = 0, size = matchings.size(); i < size; i++) {
      var matching = matchings.get(i);
      for (var pat : matching.patterns()) conquer.visit(pat, i);
    }
  }

  public void visit(@NotNull Pat pat, int nth) {
    switch (pat) {
      case Pat.Ctor ctor -> {
        var params = ctor.params();
        for (var sub : params) visit(sub, nth);
        var conditions = ctor.ref().core.clauses;
        for (int i = 0, size = conditions.size(); i < size; i++) {
          var condition = conditions.get(i);
          var matchy = PatMatcher.tryBuildSubstTerms(null, params,
            condition.patterns().view().map(Pat::toTerm),
            t -> t.normalize(tycker.state, NormalizeMode.WHNF));
          if (matchy.isOk()) {
            var ctx = new MapLocalCtx();
            condition.patterns().forEach(tern -> tern.storeBindings(ctx));
            // Do not need `ctor.storeBindings(ctx)` since they're substituted out by matchy
            checkConditions(ctx, ctor, nth, i + 1, condition.body(), matchy.get(), condition.sourcePos());
          }
        }
      }
      case Pat.Tuple tuple -> {
        for (var sub : tuple.pats()) visit(sub, nth);
      }
      default -> {}
    }
  }

  private void checkConditions(
    LocalCtx ctx, Pat ctor, int nth, int i,
    Term condition, Subst matchy, SourcePos conditionPos
  ) {
    var currentClause = matchings.get(nth);
    var newBody = currentClause.body().subst(matchy);
    var newArgs = currentClause.patterns().map(pat -> new Arg<>(new PatToTerm() {
      @Override public @NotNull Term visitCtor(Pat.@NotNull Ctor newCtor) {
        return newCtor == ctor ? condition : super.visitCtor(newCtor);
      }

      @Override public Term visit(@NotNull Pat pat) {
        if (pat instanceof Pat.Bind bind) ctx.put(bind.bind(), bind.type());
        return super.visit(pat);
      }
    }.visit(pat), pat.explicit()));
    var volynskaya = new Expander.WHNFer(tycker.state).tryUnfoldClauses(orderIndependent, newArgs, 0, matchings).getOrNull();
    if (volynskaya == null) {
      tycker.reporter.report(new ClausesProblem.Conditions(
        sourcePos, nth + 1, i, newBody, null, conditionPos, currentClause.sourcePos(), null));
      return;
    }
    if (newBody instanceof ErrorTerm error && error.description() instanceof MetaTerm hole) {
      hole.ref().conditions.append(Tuple.of(matchy, volynskaya.data()));
    } else if (volynskaya.data() instanceof ErrorTerm error && error.description() instanceof MetaTerm hole) {
      hole.ref().conditions.append(Tuple.of(matchy, newBody));
    }
    var unification = tycker.unifier(sourcePos, Ordering.Eq, ctx)
      .compare(newBody, volynskaya.data(), signature.result().subst(matchy));
    if (!unification) {
      tycker.reporter.report(new ClausesProblem.Conditions(
        sourcePos, nth + 1, i, newBody, volynskaya.data(), conditionPos, currentClause.sourcePos(), volynskaya.sourcePos()));
    }
  }
}
