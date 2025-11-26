// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.xtt;

import kala.collection.immutable.ImmutableSeq;
import kala.function.IndexedFunction;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.marker.StableWHNF;
import org.jetbrains.annotations.NotNull;

// { phi1 => rhs; ... }
public record PartialTerm(@NotNull ImmutableSeq<Clause> clauses) implements StableWHNF {
  public record Clause(@NotNull ConjCof cof, @NotNull Term tm) {
    public @NotNull Clause update(@NotNull ConjCof cof, @NotNull Term tm) {
      return cof == cof() && tm == tm() ? this : new Clause(cof, tm);
    }

    public @NotNull Clause descent(@NotNull IndexedFunction<Term, Term> f) {
      return new Clause(cof().descent(f), tm.descent(f));
    }
  }

  public @NotNull PartialTerm update(@NotNull ImmutableSeq<Clause> clauses) {
    return clauses.sameElements(clauses(), true) ? this : new PartialTerm(clauses);
  }

  @Override public @NotNull PartialTerm descent(@NotNull IndexedFunction<Term, Term> f) {
    return update(clauses().map(e -> e.descent(f)));
  }
}
