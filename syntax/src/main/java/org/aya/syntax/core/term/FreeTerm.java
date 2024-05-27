// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term;

import kala.function.IndexedFunction;
import org.aya.syntax.core.term.marker.TyckInternal;
import org.aya.syntax.ref.LocalVar;
import org.jetbrains.annotations.NotNull;

public record FreeTerm(@NotNull LocalVar name) implements TyckInternal {
  public FreeTerm(@NotNull String name) { this(LocalVar.generate(name)); }
  @Override public @NotNull Term descent(@NotNull IndexedFunction<Term, Term> f) { return this; }
  @Override public @NotNull Term bindAt(@NotNull LocalVar var, int depth) {
    if (name == var) return new LocalTerm(depth);
    return this;
  }
}
