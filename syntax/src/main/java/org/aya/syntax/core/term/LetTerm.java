// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term;

import kala.function.IndexedFunction;
import org.aya.syntax.core.Closure;
import org.jetbrains.annotations.NotNull;

public record LetTerm(@NotNull Term definedAs, @NotNull Closure body) implements Term {
  public @NotNull LetTerm update(@NotNull Term definedAs, @NotNull Closure body) {
    return definedAs == definedAs() && body == body()
      ? this
      : new LetTerm(definedAs, body);
  }

  @Override
  public @NotNull Term descent(@NotNull IndexedFunction<Term, Term> f) {
    return update(f.apply(0, definedAs), body.descent(f));
  }
}
