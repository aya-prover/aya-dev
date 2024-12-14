// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term;

import kala.collection.immutable.ImmutableSeq;
import kala.function.IndexedFunction;
import org.jetbrains.annotations.NotNull;

public record MatchTerm(
  @NotNull ImmutableSeq<Term> discriminant, @NotNull Term returns,
  @NotNull ImmutableSeq<Term.Matching> clauses
) implements Term {
  public @NotNull MatchTerm update(
    @NotNull ImmutableSeq<Term> discriminant, @NotNull Term returns,
    @NotNull ImmutableSeq<Term.Matching> clauses
  ) {
    return this.discriminant.sameElements(discriminant, true)
      && this.returns == returns
      && this.clauses.sameElements(clauses, true)
      ? this : new MatchTerm(discriminant, returns, clauses);
  }

  @Override public @NotNull Term descent(@NotNull IndexedFunction<Term, Term> f) {
    return update(
      discriminant.map(x -> f.apply(0, x)),
      f.apply(0, returns), clauses.map(clause ->
        clause.descent(t -> f.apply(clause.bindCount(), t))
      ));
  }
}
