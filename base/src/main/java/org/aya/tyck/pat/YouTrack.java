// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.pat;

import kala.collection.immutable.ImmutableSeq;
import kala.tuple.Tuple;
import kala.tuple.primitive.IntObjTuple2;
import org.aya.core.pat.Pat;
import org.aya.core.pat.PatUnify;
import org.aya.core.term.ErrorTerm;
import org.aya.core.term.MetaTerm;
import org.aya.core.term.RefTerm;
import org.aya.core.term.Term;
import org.aya.core.visitor.DeltaExpander;
import org.aya.core.visitor.Subst;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.env.LocalCtx;
import org.aya.tyck.error.UnifyInfo;
import org.aya.util.Arg;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

/**
 * YouTrack checks confluence.
 *
 * @author ice1000
 * @see PatClassifier#classify
 */
public record YouTrack(
  @NotNull ImmutableSeq<Term.Param> telescope,
  @NotNull ExprTycker tycker, @NotNull SourcePos pos
) {
  private void unifyClauses(Term result, IntObjTuple2<Term.Matching> lhsInfo, IntObjTuple2<Term.Matching> rhsInfo) {
    var lhsSubst = new Subst();
    var rhsSubst = new Subst();
    var ctx = PatUnify.unifyPat(
      lhsInfo.component2().patterns().view().map(Arg::term),
      rhsInfo.component2().patterns().view().map(Arg::term),
      lhsSubst, rhsSubst, tycker.ctx.deriveMap());
    domination(ctx, rhsSubst, lhsInfo.component1(), rhsInfo.component1(), rhsInfo.component2());
    domination(ctx, lhsSubst, rhsInfo.component1(), lhsInfo.component1(), lhsInfo.component2());
    var lhsTerm = lhsInfo.component2().body().subst(lhsSubst);
    var rhsTerm = rhsInfo.component2().body().subst(rhsSubst);
    // TODO: Currently all holes at this point are in an ErrorTerm
    if (lhsTerm instanceof ErrorTerm error && error.description() instanceof MetaTerm hole) {
      hole.ref().conditions.append(Tuple.of(lhsSubst, rhsTerm));
    } else if (rhsTerm instanceof ErrorTerm error && error.description() instanceof MetaTerm hole) {
      hole.ref().conditions.append(Tuple.of(rhsSubst, lhsTerm));
    }
    var resultSubst = DeltaExpander.buildSubst(telescope, Arg.mapSeq(lhsInfo.component2().patterns(), Pat::toTerm));
    resultSubst.add(lhsSubst);
    tycker.unifyReported(lhsTerm, rhsTerm, result.subst(resultSubst), pos, ctx, comparison ->
      new ClausesProblem.Confluence(pos, rhsInfo.component1() + 1, lhsInfo.component1() + 1,
        comparison, new UnifyInfo(tycker.state), rhsInfo.component2().sourcePos(), lhsInfo.component2().sourcePos()));
  }

  private void domination(LocalCtx ctx, Subst rhsSubst, int lhsIx, int rhsIx, Term.Matching matching) {
    if (rhsSubst.map().valuesView().allMatch(dom ->
      dom instanceof RefTerm(var ref) && ctx.contains(ref))
    ) tycker.reporter.report(new ClausesProblem.Domination(
      lhsIx + 1, rhsIx + 1, matching.sourcePos()));
  }

  public void check(@NotNull ClauseTycker.PatResult clauses, @NotNull ImmutableSeq<PatClassifier.PatClass<ImmutableSeq<Arg<Term>>>> mct) {
    mct.forEach(results -> {
      var contents = results.cls()
        .mapToObj(i -> Pat.Preclause.lift(clauses.clauses().get(i))
          .map(matching -> IntObjTuple2.of(i, matching)))
        .flatMap(it -> it);
      for (int i = 1, size = contents.size(); i < size; i++)
        unifyClauses(clauses.result(), contents.get(i - 1), contents.get(i));
    });
  }
}
