// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.repr;

import kala.function.IndexedFunction;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.marker.StableWHNF;
import org.jetbrains.annotations.NotNull;

public record StringTerm(@NotNull String string) implements StableWHNF {
  @Override public @NotNull StringTerm descent(@NotNull IndexedFunction<Term, Term> f) {
    return this;
  }
}
