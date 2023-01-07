// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.pat;

import kala.collection.immutable.ImmutableSeq;
import kala.tuple.Tuple;
import org.aya.core.def.FnDef;
import org.aya.core.pat.Pat;
import org.aya.core.term.ErrorTerm;
import org.aya.core.term.FnCall;
import org.aya.core.term.MetaTerm;
import org.aya.core.term.Term;
import org.aya.core.visitor.AyaRestrSimplifier;
import org.aya.core.visitor.DeltaExpander;
import org.aya.core.visitor.Subst;
import org.aya.generic.util.NormalizeMode;
import org.aya.guest0x0.cubical.CofThy;
import org.aya.guest0x0.cubical.Partial;
import org.aya.guest0x0.cubical.Restr;
import org.aya.tyck.env.MapLocalCtx;
import org.aya.tyck.error.UnifyInfo;
import org.aya.tyck.tycker.UnifiedTycker;
import org.aya.util.Arg;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

/**
 * <code>IApplyConfluence</code> lmao
 *
 * @author ice1000
 */
public record Conquer(
  @NotNull FnDef def,
  @NotNull ImmutableSeq<Term.Matching> matchings,
  @NotNull SourcePos sourcePos,
  boolean orderIndependent,
  @NotNull UnifiedTycker tycker
) {
  public static void against(
    @NotNull FnDef def, boolean orderIndependent,
    @NotNull UnifiedTycker tycker, @NotNull SourcePos pos
  ) {
    var matchings = def.body.getRightValue();
    var conquer = new Conquer(def, matchings, pos, orderIndependent, tycker);
    for (int i = 0, size = matchings.size(); i < size; i++) {
      var matching = matchings.get(i);
      for (var pat : matching.patterns()) conquer.visit(pat.term(), i);
    }
  }

  public void visit(@NotNull Pat pat, int nth) {
    switch (pat) {
      case Pat.Ctor ctor -> {
        var params = ctor.params();
        for (var sub : params) visit(sub.term(), nth);
        var ctorDef = ctor.ref().core;
        assert ctorDef.selfTele.sizeEquals(params);
        // Con tele |-> pattern tele
        var subst = new Subst(
          ctorDef.selfTele.view().map(Term.Param::ref),
          params.view().map(t -> t.term().toTerm())
        );
        // The split, but typed under current context
        var clauses = (Partial.Split<Term>) AyaRestrSimplifier.INSTANCE
          .mapSplit(ctorDef.clauses, t -> t.subst(subst));
        var faces = clauses.clauses();
        for (int i = 0, size = faces.size(); i < size; i++) {
          checkConditions(nth, i + 1, faces.get(i));
        }
      }
      case Pat.Tuple tuple -> {
        for (var sub : tuple.pats()) visit(sub.term(), nth);
      }
      default -> {}
    }
  }

  private void checkConditions(int nth, int i, Restr.Side<Term> condition) {
    var ctx = new MapLocalCtx();
    var currentClause = matchings.get(nth);
    CofThy.conv(condition.cof(), new Subst(), subst -> {
      // We should also restrict the current clause body under `condition`.
      var newBody = currentClause.body().subst(subst);
      var args = Arg.mapSeq(currentClause.patterns(), t -> t.toTerm().subst(subst));
      var matchResult = new FnCall(def.ref, 0, args).normalize(tycker.state, NormalizeMode.WHNF).subst(subst);
      currentClause.patterns().forEach(p -> p.term().storeBindings(ctx, subst));
      if (newBody instanceof ErrorTerm error && error.description() instanceof MetaTerm hole) {
        hole.ref().conditions.append(Tuple.of(subst, matchResult));
      } else if (matchResult instanceof ErrorTerm error && error.description() instanceof MetaTerm hole) {
        var newSubst = new Subst(hole.ref().contextTele.map(Term.Param::ref), hole.contextArgs().map(Arg::term));
        hole.ref().conditions.append(Tuple.of(newSubst, newBody));
      }
      var retSubst = DeltaExpander.buildSubst(def.telescope, args);
      retSubst.add(subst);
      return tycker.unifyReported(newBody, matchResult, def.result.subst(retSubst),
        sourcePos, ctx, comparison -> new ClausesProblem.Conditions(
          sourcePos, currentClause.sourcePos(), nth + 1, i, args, new UnifyInfo(tycker.state), comparison));
    });
  }
}
