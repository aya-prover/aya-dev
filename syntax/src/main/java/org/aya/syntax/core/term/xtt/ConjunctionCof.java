// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.xtt;

import kala.collection.immutable.ImmutableSeq;
import kala.function.IndexedFunction;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;

public record ConjunctionCof(@NotNull ImmutableSeq<CofElement> elements) {
  public @NotNull ConjunctionCof add(@NotNull CofElement c) {
    return new ConjunctionCof(elements.appended(c));
  }
  public @NotNull ConjunctionCof descent(@NotNull IndexedFunction<Term, Term> f) {
    return new ConjunctionCof(elements().map(e -> e.descent(f)));
  }
}
