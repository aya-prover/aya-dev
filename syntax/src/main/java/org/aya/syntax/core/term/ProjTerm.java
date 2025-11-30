// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term;

import org.aya.generic.TermVisitor;
import org.aya.syntax.core.annotation.Closed;
import org.aya.syntax.core.term.marker.BetaRedex;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

public record ProjTerm(@NotNull Term of, boolean fst) implements BetaRedex {
  public static final int INDEX_FST = 1;
  public static final int INDEX_SND = 2;

  public @NotNull Term update(@NotNull Term of, boolean fst) {
    return this.of == of && this.fst == fst ? this : new ProjTerm(of, fst).make();
  }

  @Override public @NotNull Term descent(@NotNull TermVisitor visitor) {
    return update(visitor.term(of), fst);
  }

  public static @Closed @NotNull Term fst(@Closed @NotNull Term of) {
    return make(of, true);
  }

  public static @Closed @NotNull Term snd(@Closed @NotNull Term of) {
    return make(of, false);
  }

  /** Unwrap {@code of.index} if possible */
  public static @Closed @NotNull Term make(@Closed @NotNull Term of, boolean fst) {
    return new ProjTerm(of, fst).make();
  }

  /** Unwrap the {@param material} if possible. */
  @Override public @NotNull Term make(@NotNull UnaryOperator<Term> mapper) {
    if (of instanceof TupTerm(var lhs, var rhs)) return mapper.apply(fst ? lhs : rhs);
    return this;
  }

  public int index() {
    return fst ? INDEX_FST : INDEX_SND;
  }
}
