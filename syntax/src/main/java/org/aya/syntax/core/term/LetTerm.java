// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term;

import kala.function.IndexedFunction;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.term.marker.BetaRedex;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

public record LetTerm(@NotNull Term definedAs, @NotNull Closure body) implements Term, BetaRedex {
  public @NotNull LetTerm update(@NotNull Term definedAs, @NotNull Closure body) {
    return definedAs == definedAs() && body == body()
      ? this
      : new LetTerm(definedAs, body);
  }

  @Override
  public @NotNull Term descent(@NotNull IndexedFunction<Term, Term> f) {
    return update(f.apply(0, definedAs), body.descent(f));
  }

  @Override
  public @NotNull Term make(@NotNull UnaryOperator<Term> mapper) {
    return mapper.apply(body.apply(definedAs));
  }
}
