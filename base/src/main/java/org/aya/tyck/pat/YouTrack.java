// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.pat;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableSet;
import kala.collection.mutable.MutableTreeSet;
import org.aya.syntax.core.def.FnClauseBody;
import org.aya.syntax.core.term.FreeTerm;
import org.aya.syntax.core.term.Param;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.ref.LocalCtx;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.error.ClausesProblem;
import org.aya.tyck.error.UnifyInfo;
import org.aya.util.position.SourcePos;
import org.aya.util.position.WithPos;
import org.jetbrains.annotations.NotNull;

/**
 * YouTrack checks confluence.
 *
 * @see PatClassifier#classify
 */
public record YouTrack(
  @NotNull ImmutableSeq<Param> telescope,
  @NotNull ExprTycker tycker, @NotNull SourcePos pos
) {
  private record Info(int ix, @NotNull WithPos<Term.Matching> matching) { }
  private void unifyClauses(
    Term result, Info lhsInfo, Info rhsInfo,
    MutableSet<ClausesProblem.Domination> doms
  ) {
    var ctx = tycker.localCtx();
    var unifyResult = PatUnify.unifyPat(
      lhsInfo.matching.data().patterns().view(),
      rhsInfo.matching.data().patterns().view(), ctx,
      MutableList.create(), MutableList.create());
    var unify = unifyResult.unify();
    domination(ctx, unify.rhsSubst(), lhsInfo.ix, rhsInfo.ix, rhsInfo.matching, doms);
    domination(ctx, unify.lhsSubst(), rhsInfo.ix, lhsInfo.ix, lhsInfo.matching, doms);
    var lhsTerm = lhsInfo.matching.data().body().instTele(unify.lhsSubst().view());
    var rhsTerm = rhsInfo.matching.data().body().instTele(unify.rhsSubst().view());
    // // TODO: Currently all holes at this point are in an ErrorTerm
    // if (lhsTerm instanceof ErrorTerm error && error.description() instanceof MetaCall hole) {
    //   hole.ref().conditions.append(Tuple.of(lhsSubst, rhsTerm));
    // }
    // if (rhsTerm instanceof ErrorTerm error && error.description() instanceof MetaCall hole) {
    //   hole.ref().conditions.append(Tuple.of(rhsSubst, lhsTerm));
    // }
    result = tycker.whnf(result.instTele(unifyResult.args().view()));
    tycker.unifyTermReported(lhsTerm, rhsTerm, result, pos, comparison ->
      new ClausesProblem.Confluence(pos, rhsInfo.ix + 1, lhsInfo.ix + 1,
        comparison, new UnifyInfo(tycker.state), rhsInfo.matching.sourcePos(), lhsInfo.matching.sourcePos()));
  }

  private void domination(
    LocalCtx ctx, Seq<Term> subst,
    int lhsIx, int rhsIx, WithPos<Term.Matching> matching,
    MutableSet<ClausesProblem.Domination> doms
  ) {
    if (subst.allMatch(dom -> dom instanceof FreeTerm(var ref) && ctx.contains(ref)))
      doms.add(new ClausesProblem.Domination(lhsIx + 1, rhsIx + 1, matching.sourcePos()));
  }

  public void check(@NotNull FnClauseBody body, @NotNull Term type) {
    var doms = MutableTreeSet.<ClausesProblem.Domination>create();
    body.classes.forEach(results -> {
      var contents = results.cls().mapToObj(i -> new Info(i, body.clauses.get(i)));
      for (int i = 1, size = contents.size(); i < size; i++) {
        try (var _ = tycker.subscope()) {
          unifyClauses(type, contents.get(i - 1), contents.get(i), doms);
        }
      }
    });
    tycker.reporter.reportAll(doms.view());
  }
}
