// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.xtt;

import kala.function.IndexedFunction;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;

public record AndCofTerm(@NotNull Term lhs, @NotNull Term rhs) implements Term {
  public @NotNull AndCofTerm update(@NotNull Term lhs, @NotNull Term rhs) {
    return lhs == lhs() &&  rhs == rhs() ? this : new AndCofTerm(lhs, rhs);
  }

  @Override public @NotNull Term descent(@NotNull IndexedFunction<Term, Term> f) {
    return update(f.apply(0, lhs), f.apply(0, rhs));
  }
}
