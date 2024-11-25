// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term;

import kala.function.IndexedFunction;
import org.aya.syntax.core.term.marker.StableWHNF;
import org.jetbrains.annotations.NotNull;

/**
 * @author re-xyr
 */
public record TupTerm(@NotNull Term lhs, @NotNull Term rhs) implements StableWHNF {
  private @NotNull TupTerm update(@NotNull Term lhs, @NotNull Term rhs) {
    return lhs == this.lhs && rhs == this.rhs ? this : new TupTerm(lhs, rhs);
  }

  @Override public @NotNull Term descent(@NotNull IndexedFunction<Term, Term> f) {
    return update(f.apply(0, lhs), f.apply(0, rhs));
  }
}
