// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.pat;

import org.aya.api.error.SourcePos;
import org.aya.api.util.NormalizeMode;
import org.aya.core.def.DataDef;
import org.aya.core.def.Def;
import org.aya.core.pat.Pat;
import org.aya.core.pat.PatMatcher;
import org.aya.core.term.Term;
import org.aya.core.visitor.Normalizer;
import org.aya.core.visitor.Substituter;
import org.aya.generic.Matching;
import org.aya.tyck.ExprTycker;
import org.aya.util.Ordering;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.MutableMap;
import org.glavo.kala.collection.mutable.MutableSet;
import org.glavo.kala.tuple.Unit;
import org.glavo.kala.tuple.primitive.IntTuple2;
import org.jetbrains.annotations.NotNull;

/**
 * The name is short for "condition checker"
 *
 * @author ice1000
 */
public record Conquer(
  @NotNull ImmutableSeq<Matching<Pat, Term>> matchings,
  @NotNull MutableSet<IntTuple2> comparisons,
  @NotNull SourcePos sourcePos,
  @NotNull Def.Signature signature,
  @NotNull ExprTycker tycker
) implements Pat.Visitor<Integer, Unit> {
  public static void against(
    @NotNull ImmutableSeq<Matching<Pat, Term>> matchings,
    @NotNull ExprTycker tycker, @NotNull SourcePos pos, @NotNull Def.Signature signature
  ) {
    var unificationBag = MutableSet.<IntTuple2>of();
    for (var matching : matchings) {
      var patterns = matching.patterns();
      for (int i = 0, size = patterns.size(); i < size; i++) {
        var pat = patterns.get(i);
        pat.accept(new Conquer(matchings, unificationBag, pos, signature, tycker), i);
      }
    }
  }

  @Override public Unit visitBind(Pat.@NotNull Bind bind, Integer nth) {
    return Unit.unit();
  }

  @Override public Unit visitTuple(Pat.@NotNull Tuple tuple, Integer nth) {
    for (var pat : tuple.pats()) pat.accept(this, nth);
    return Unit.unit();
  }

  @Override public Unit visitCtor(Pat.@NotNull Ctor ctor, Integer nth) {
    var params = ctor.params();
    for (var pat : params) pat.accept(this, nth);
    checkConditions(nth, params, ctor.ref().core);
    return Unit.unit();
  }

  private void checkConditions(int nth, ImmutableSeq<Pat> params, DataDef.Ctor ctor) {
    for (var condition : ctor.clauses()) {
      var matchy = PatMatcher.tryBuildSubstTerms(params, condition.patterns().view().map(Pat::toTerm));
      if (matchy == null) continue;
      var currentClause = matchings.get(nth);
      var newBody = currentClause.body().subst(matchy);
      var newArgs = currentClause.patterns().map(Pat::toArg);
      var volynskaya = Normalizer.INSTANCE.tryUnfoldClauses(NormalizeMode.WHNF, newArgs,
        new Substituter.TermSubst(MutableMap.of()), matchings);
      if (volynskaya == null) {
        // TODO[ice]: unfold foiled, cannot check confluence over conditions
        throw new ExprTycker.TyckerException();
      }
      // TODO[ice]: the tycker.localCtx is probably not suitable in this case. We need to type both
      //  bodies, where the contexts can be obtained during the tycking of the terms
      var unification = tycker.unifier(sourcePos, Ordering.Eq, tycker.localCtx)
        .compare(newBody, volynskaya, signature.result().subst(matchy));
      if (!unification) {
        // TODO[ice]: not confluence over conditions
        throw new ExprTycker.TyckerException();
      }
    }
  }

  @Override public Unit visitAbsurd(Pat.@NotNull Absurd absurd, Integer nth) {
    return Unit.unit();
  }
}
