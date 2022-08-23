// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import org.aya.guest0x0.cubical.Formula;
import org.jetbrains.annotations.NotNull;

public sealed interface PrimTerm extends Term {
  record Mula(@NotNull Formula<Term> asFormula) implements PrimTerm {
    public static final @NotNull Mula LEFT = new Mula(new Formula.Lit<>(true));
    public static final @NotNull Mula RIGHT = new Mula(new Formula.Lit<>(false));
    public static @NotNull Mula inv(@NotNull Term term) {
      return new Mula(new Formula.Inv<>(term));
    }
    public static @NotNull Mula and(@NotNull Term lhs, @NotNull Term rhs) {
      return conn(true, lhs, rhs);
    }
    public static @NotNull Mula or(@NotNull Term lhs, @NotNull Term rhs) {
      return conn(false, lhs, rhs);
    }
    public static @NotNull Mula conn(boolean isAnd, @NotNull Term lhs, @NotNull Term rhs) {
      return new Mula(new Formula.Conn<>(isAnd, lhs, rhs));
    }
  }

  record Str(@NotNull String string) implements PrimTerm {
  }
}
