// Copyright (c) 2020-2026 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.xtt;

import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.TermVisitor;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.marker.StableWHNF;
import org.jetbrains.annotations.NotNull;

// { phi1 => rhs; ... }
public record PartialTerm(@NotNull ImmutableSeq<Clause> clauses) implements StableWHNF {
  public record Clause(@NotNull DisjCofNF cof, @NotNull Term tm) {
    public @NotNull Clause update(@NotNull DisjCofNF cof, @NotNull Term tm) {
      return cof == cof() && tm == tm() ? this : new Clause(cof, tm);
    }

    public @NotNull Clause descent(@NotNull TermVisitor visitor) {
      return update(cof().descent(visitor), visitor.term(tm()));
    }
  }

  public @NotNull PartialTerm update(@NotNull ImmutableSeq<Clause> clauses) {
    return clauses.sameElements(clauses(), true) ? this : new PartialTerm(clauses);
  }

  @Override public @NotNull PartialTerm descent(@NotNull TermVisitor visitor) {
    return update(clauses().map(e -> e.descent(visitor)));
  }
}
