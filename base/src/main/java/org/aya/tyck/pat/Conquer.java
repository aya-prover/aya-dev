// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.pat;

import org.aya.core.pat.Pat;
import org.aya.core.pat.PatMatcher;
import org.aya.core.term.Term;
import org.aya.generic.Matching;
import org.aya.tyck.ExprTycker;
import org.glavo.kala.collection.immutable.ImmutableSeq;
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
  @NotNull ExprTycker tycker
) implements Pat.Visitor<Integer, Unit> {
  public static void against(@NotNull ImmutableSeq<Matching<Pat, Term>> matchings, @NotNull ExprTycker tycker) {
    var unificationBag = MutableSet.<IntTuple2>of();
    for (var matching : matchings) {
      var patterns = matching.patterns();
      for (int i = 0, size = patterns.size(); i < size; i++) {
        var pat = patterns.get(i);
        pat.accept(new Conquer(matchings, unificationBag, tycker), i);
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
    for (var condition : ctor.ref().core.clauses()) {
      var matchy = PatMatcher.tryBuildSubstTerms(params, condition.patterns().view().map(Pat::toTerm));
      if (matchy == null) continue;
      var newBody = matchings.get(nth).body().subst(matchy);
    }
    return Unit.unit();
  }

  @Override public Unit visitAbsurd(Pat.@NotNull Absurd absurd, Integer nth) {
    return Unit.unit();
  }
}
