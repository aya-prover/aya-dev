// Copyright (c) 2020-2026 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.xtt;

import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.TermVisitor;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;

public interface CofNF {
  record Conj(@NotNull ImmutableSeq<EqCofTerm> elements) {
    public @NotNull CofNF.Conj descent(@NotNull TermVisitor visitor) {
      if (elements().isEmpty()) return this;
      return update(elements().map(e -> e.descent(visitor)));
    }

    public @NotNull CofNF.Conj update(@NotNull ImmutableSeq<EqCofTerm> elements) {
      return elements.sameElements(elements(), true) ? this : new Conj(elements);
    }
  }
  record Disj(@NotNull ImmutableSeq<Conj> elements) implements Term {
    public @NotNull CofNF.Disj descent(@NotNull TermVisitor visitor) {
      if (elements().isEmpty()) return this;
      return update(elements().map(e -> e.descent(visitor)));
    }

    public @NotNull CofNF.Disj update(@NotNull ImmutableSeq<Conj> elements) {
      return elements.sameElements(elements(), true) ? this : new Disj(elements);
    }
  }
  /// lhs = rhs
  record EqCofTerm(@NotNull Term lhs, @NotNull Term rhs) {
    public @NotNull CofNF.EqCofTerm descent(@NotNull TermVisitor visitor) {
      var newLhs = visitor.term(lhs);
      var newRhs = visitor.term(rhs);
      if (newLhs == lhs && newRhs == rhs) return this;
      return new EqCofTerm(newLhs, newRhs);
    }
  }
}
