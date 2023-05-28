// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import org.aya.core.pat.Pat;
import org.aya.generic.Nested;
import org.aya.ref.LocalVar;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

public record LetTerm(
  @NotNull LocalVar bind,
  @NotNull Term definedAs,
  @NotNull Term body
) implements Term, Nested<Tuple2<LocalVar, Term>, Term, LetTerm> {
  /**
   * Substitute {@link LetTerm#bind} in {@link LetTerm#body} with {@link LetTerm#definedAs}.
   * In order to avoid normalizing {@code big} multiple times in {@code (\ x => (x, ..., x)) big}
   */
  public @NotNull Term inline(@NotNull UnaryOperator<Term> inliner) {
    return body.subst(bind, inliner.apply(definedAs));
  }

  public @NotNull LetTerm update(@NotNull Term definedAs, @NotNull Term body) {
    return this.definedAs == definedAs && this.body == body
      ? this
      : new LetTerm(bind, definedAs, body);
  }

  @Override
  public @NotNull Term descent(@NotNull UnaryOperator<Term> f, @NotNull UnaryOperator<Pat> g) {
    return update(f.apply(definedAs), f.apply(body));
  }

  @Override
  public @NotNull Tuple2<LocalVar, Term> param() {
    return Tuple.of(bind, definedAs);
  }
}
