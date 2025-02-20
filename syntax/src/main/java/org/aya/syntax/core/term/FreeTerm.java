// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term;

import kala.function.IndexedFunction;
import org.aya.syntax.ref.LocalVar;
import org.jetbrains.annotations.NotNull;

public record FreeTerm(@NotNull LocalVar name) implements FreeTermLike {
  public FreeTerm(@NotNull String name) { this(LocalVar.generate(name)); }

  @Override public @NotNull Term descent(@NotNull IndexedFunction<Term, Term> f) { return this; }
}
