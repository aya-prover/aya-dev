// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term;

import kala.collection.immutable.ImmutableSeq;
import kala.function.IndexedFunction;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

public record MatchTerm(
  @NotNull ImmutableSeq<Term> discriminant,
  @NotNull ImmutableSeq<Term.Matching> clauses
) implements Term {
  public @NotNull MatchTerm update(
    @NotNull ImmutableSeq<Term> discriminant,
    @NotNull ImmutableSeq<Term.Matching> clauses
  ) {
    return this.discriminant.sameElements(discriminant, true)
      && this.clauses.sameElements(clauses, true)
      ? this
      : new MatchTerm(discriminant, clauses);
  }

  @Override
  public @NotNull Term descent(@NotNull IndexedFunction<Term, Term> f) {
    return update(discriminant.map(x -> f.apply(0, x)), clauses.map(clause ->
      clause.descent(
        t -> f.apply(clause.bindCount(), t),
        UnaryOperator.identity())
    ));
  }
}
