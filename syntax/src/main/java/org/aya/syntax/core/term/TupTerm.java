// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term;

import kala.collection.immutable.ImmutableSeq;
import kala.function.IndexedFunction;
import org.aya.syntax.core.term.marker.StableWHNF;
import org.jetbrains.annotations.NotNull;

/**
 * @author re-xyr
 */
public record TupTerm(@NotNull ImmutableSeq<Term> items) implements StableWHNF {
  private @NotNull TupTerm update(@NotNull ImmutableSeq<Term> items) {
    return items.sameElements(items(), true) ? this : new TupTerm(items);
  }

  @Override
  public @NotNull Term descent(@NotNull IndexedFunction<Term, Term> f) {
    return update(items.map(x -> f.apply(0, x)));
  }
}
