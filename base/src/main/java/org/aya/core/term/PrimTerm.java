// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import org.aya.guest0x0.cubical.Formula;
import org.aya.guest0x0.cubical.Restr;
import org.jetbrains.annotations.NotNull;

public sealed interface PrimTerm extends Term {
  final class End implements PrimTerm {
    private final boolean isRight;

    public static End LEFT = new End(false);
    public static End RIGHT = new End(true);

    private End(boolean isRight) {
      this.isRight = isRight;
    }

    public boolean isRight() {
      return isRight;
    }

    @Override public @NotNull Formula<Term> asFormula() {
      return new Formula.Lit<>(!isRight);
    }
  }

  record Str(@NotNull String string) implements PrimTerm {
  }

  /** the face (restr) being specified in partial types */
  record Cof(@NotNull Restr<Term> restr) implements PrimTerm {}
}
