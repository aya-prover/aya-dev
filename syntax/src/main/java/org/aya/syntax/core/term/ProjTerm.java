// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term;

import kala.function.IndexedFunction;
import org.aya.syntax.core.term.marker.BetaRedex;
import org.jetbrains.annotations.NotNull;

/**
 * @param index can only be 0 or 1
 */
public record ProjTerm(@NotNull Term of, int index) implements BetaRedex {
  public @NotNull ProjTerm update(@NotNull Term of, int index) {
    return this.of == of && this.index == index ? this : new ProjTerm(of, index);
  }

  @Override public @NotNull Term descent(@NotNull IndexedFunction<Term, Term> f) {
    return update(f.apply(0, of), index);
  }

  /** Unwrap {@code of.index} if possible */
  public static @NotNull Term make(@NotNull Term of, int index) {
    return new ProjTerm(of, index).make();
  }

  /** Unwrap the {@param material} if possible. */
  @Override public @NotNull Term make() {
    if (of instanceof TupTerm(var lhs, var rhs)) return index == 0 ? lhs : rhs;
    return this;
  }
}
