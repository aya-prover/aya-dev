// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.pat;

import kala.collection.immutable.ImmutableSeq;
import kala.tuple.Tuple;
import org.aya.core.def.Def;
import org.aya.core.pat.Pat;
import org.aya.core.term.ErrorTerm;
import org.aya.core.term.MetaTerm;
import org.aya.core.term.Term;
import org.aya.core.visitor.AyaRestrSimplifier;
import org.aya.core.visitor.Expander;
import org.aya.core.visitor.Subst;
import org.aya.guest0x0.cubical.CofThy;
import org.aya.guest0x0.cubical.Partial;
import org.aya.guest0x0.cubical.Restr;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.env.MapLocalCtx;
import org.aya.util.Ordering;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

/**
 * <code>IApplyConfluence</code> lmao
 *
 * @author ice1000
 */
public record Conquer(
  @NotNull ImmutableSeq<Term.Matching> matchings,
  @NotNull SourcePos sourcePos,
  @NotNull Def.Signature<?> signature,
  boolean orderIndependent,
  @NotNull ExprTycker tycker
) {
  public static void against(
    @NotNull ImmutableSeq<Term.Matching> matchings, boolean orderIndependent,
    @NotNull ExprTycker tycker, @NotNull SourcePos pos, @NotNull Def.Signature<?> signature
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
        var ctorDef = ctor.ref().core;
        assert ctorDef.selfTele.sizeEquals(params);
        // Con tele |-> pattern tele
        var subst = new Subst(
          ctorDef.selfTele.view().map(Term.Param::ref),
          params.view().map(Pat::toTerm)
        );
        // The split, but typed under current context
        var clauses = (Partial.Split<Term>) AyaRestrSimplifier.INSTANCE
          .mapSplit(ctorDef.clauses, t -> t.subst(subst));
        var faces = clauses.clauses();
        for (int i = 0, size = faces.size(); i < size; i++) {
          checkConditions(nth, i + 1, faces.get(i), subst);
        }
      }
      case Pat.Tuple tuple -> {
        for (var sub : tuple.pats()) visit(sub, nth);
      }
      default -> {}
    }
  }

  private void checkConditions(int nth, int i, Restr.Side<Term> condition, Subst matchy) {
    var ctx = new MapLocalCtx();
    var currentClause = matchings.get(nth);
    CofThy.conv(condition.cof(), matchy, subst -> {
      // We should also restrict the current clause body under `condition`.
      var newBody = currentClause.body().subst(subst);
      var matchResult = new Expander.WHNFer(tycker.state).tryUnfoldClauses(orderIndependent,
        currentClause.patterns().map(p -> p.toArg().descent(t -> t.subst(subst))),
        0, matchings).map(w -> w.map(t -> t.subst(subst)));
      currentClause.patterns().forEach(p -> p.storeBindings(ctx));
      if (matchResult.isEmpty()) {
        tycker.reporter.report(new ClausesProblem.Conditions(
          sourcePos, nth + 1, i, newBody, null, currentClause.sourcePos(), null));
        return true;
      }
      var anotherClause = matchResult.get();
      if (newBody instanceof ErrorTerm error && error.description() instanceof MetaTerm hole) {
        hole.ref().conditions.append(Tuple.of(matchy, anotherClause.data()));
      } else if (anotherClause.data() instanceof ErrorTerm error && error.description() instanceof MetaTerm hole) {
        hole.ref().conditions.append(Tuple.of(matchy, newBody));
      }
      var unification = tycker.unifier(sourcePos, Ordering.Eq, ctx)
        .compare(newBody, anotherClause.data(), signature.result().subst(matchy));
      if (!unification) {
        tycker.reporter.report(new ClausesProblem.Conditions(
          sourcePos, nth + 1, i, newBody, anotherClause.data(), currentClause.sourcePos(), anotherClause.sourcePos()));
      }
      return unification;
    });
  }
}
