// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.pat;

import org.aya.api.error.SourcePos;
import org.aya.api.util.Arg;
import org.aya.api.util.NormalizeMode;
import org.aya.core.def.Def;
import org.aya.core.pat.Pat;
import org.aya.core.pat.PatMatcher;
import org.aya.core.pat.PatToTerm;
import org.aya.core.term.Term;
import org.aya.core.visitor.Normalizer;
import org.aya.core.visitor.Substituter;
import org.aya.generic.Matching;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.LocalCtx;
import org.aya.tyck.error.ConditionError;
import org.aya.util.Ordering;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.MutableMap;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * The name is short for "condition checker"
 *
 * @author ice1000
 */
public record Conquer(
  @NotNull ImmutableSeq<Matching<Pat, Term>> matchings,
  @NotNull SourcePos sourcePos,
  @NotNull LocalCtx localCtx,
  @NotNull Def.Signature signature,
  @NotNull ExprTycker tycker
) implements Pat.Visitor<Integer, Unit> {
  public static void against(
    @NotNull ImmutableSeq<Matching<Pat, Term>> matchings, @NotNull LocalCtx localCtx,
    @NotNull ExprTycker tycker, @NotNull SourcePos pos, @NotNull Def.Signature signature
  ) {
    for (int i = 0, size = matchings.size(); i < size; i++) {
      var matching = matchings.get(i);
      for (var pat : matching.patterns())
        pat.accept(new Conquer(matchings, pos, localCtx, signature, tycker), i);
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
    var conditions = ctor.ref().core.clauses();
    for (int i = 0, size = conditions.size(); i < size; i++) {
      var condition = conditions.get(i);
      var matchy = PatMatcher.tryBuildSubstTerms(params, condition.patterns().view().map(Pat::toTerm));
      if (matchy != null) {
        var currentClause = matchings.get(nth);
        var newBody = currentClause.body().subst(matchy);
        var newArgs = currentClause.patterns().map(pat -> new Arg<>(pat.accept(new PatToTerm() {
          @Override public Term visitCtor(Pat.@NotNull Ctor newCtor, Unit unit) {
            if (newCtor == ctor) return condition.body();
            return super.visitCtor(newCtor, unit);
          }
        }, Unit.unit()), pat.explicit()));
        var volynskaya = Normalizer.INSTANCE.tryUnfoldClauses(NormalizeMode.WHNF, newArgs,
          new Substituter.TermSubst(MutableMap.of()), matchings);
        if (volynskaya == null) {
          tycker.metaContext.report(new ConditionError(sourcePos, nth, i, newBody, null));
          throw new ExprTycker.TyckInterruptedException();
        }
        var unification = tycker.unifier(sourcePos, Ordering.Eq, localCtx)
          .compare(newBody, volynskaya, signature.result().subst(matchy));
        if (!unification) {
          tycker.metaContext.report(new ConditionError(sourcePos, nth, i, newBody, volynskaya));
          throw new ExprTycker.TyckInterruptedException();
        }
      }
    }
    return Unit.unit();
  }

  @Override public Unit visitAbsurd(Pat.@NotNull Absurd absurd, Integer nth) {
    return Unit.unit();
  }
}
