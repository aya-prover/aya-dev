// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import org.aya.core.pat.PatMatcher;
import org.aya.util.Arg;
import org.jetbrains.annotations.NotNull;

public record MatchTerm(
  @NotNull ImmutableSeq<Term> discriminant,
  @NotNull ImmutableSeq<Matching> clauses
) implements Term {
  public @NotNull Option<Term> tryMatch() {
    for (var clause : clauses) {
      var subst = PatMatcher.tryBuildSubst(
        false,
        clause.patterns(),
        // TODO[wsx,kiva]: args in match
        discriminant.view().map(x -> new Arg<>(x, true)));
      if (subst.isOk()) {
        return Option.some(clause.body().rename().subst(subst.get()));
      } else if (subst.getErr()) return Option.none();
    }
    return Option.none();
  }
}
