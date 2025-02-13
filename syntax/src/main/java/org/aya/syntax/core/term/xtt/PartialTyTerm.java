// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.xtt;

import kala.function.IndexedFunction;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.marker.Formation;
import org.aya.syntax.core.term.marker.StableWHNF;
import org.jetbrains.annotations.NotNull;

public record PartialTyTerm(@NotNull Term lhs, @NotNull Term rhs, @NotNull  Term ty) implements StableWHNF, Formation {
  public @NotNull PartialTyTerm update(@NotNull Term lhs, @NotNull Term rhs, @NotNull  Term ty) {
    return lhs == lhs() && rhs == rhs() && ty == ty() ? this : new PartialTyTerm(lhs, rhs, ty);
  }

  @Override public @NotNull PartialTyTerm descent(@NotNull IndexedFunction<Term, Term> f) {
    return update(f.apply(0, lhs), f.apply(1, rhs), f.apply(0,ty));
  }
}
