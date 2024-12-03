// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term;

import kala.function.IndexedFunction;
import org.aya.syntax.core.term.marker.BetaRedex;
import org.jetbrains.annotations.NotNull;

public record ProjTerm(@NotNull Term of, boolean fst) implements BetaRedex {
  public static final int INDEX_FST = 1;
  public static final int INDEX_SND = 2;

  public @NotNull ProjTerm update(@NotNull Term of, boolean fst) {
    return this.of == of && this.fst == fst ? this : new ProjTerm(of, fst);
  }

  @Override public @NotNull Term descent(@NotNull IndexedFunction<Term, Term> f) {
    return update(f.apply(0, of), fst);
  }

  public static @NotNull Term fst(@NotNull Term of) {
    return make(of, true);
  }

  public static @NotNull Term snd(@NotNull Term of) {
    return make(of, false);
  }

  /** Unwrap {@code of.index} if possible */
  public static @NotNull Term make(@NotNull Term of, boolean fst) {
    return new ProjTerm(of, fst).make();
  }

  /** Unwrap the {@param material} if possible. */
  @Override public @NotNull Term make() {
    if (of instanceof TupTerm(var lhs, var rhs)) return fst ? lhs : rhs;
    return this;
  }

  public int index() {
    return fst ? INDEX_FST : INDEX_SND;
  }
}
