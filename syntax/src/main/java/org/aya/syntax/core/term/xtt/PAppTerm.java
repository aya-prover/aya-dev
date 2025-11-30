// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.xtt;

import kala.function.IndexedFunction;
import org.aya.generic.TermVisitor;
import org.aya.syntax.core.term.LamTerm;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.marker.BetaRedex;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

public record PAppTerm(@NotNull Term fun, @NotNull Term arg, @NotNull Term a, @NotNull Term b) implements BetaRedex {
  public @NotNull Term update(
    @NotNull Term fun, @NotNull Term arg, @NotNull Term a, @NotNull Term b,
    UnaryOperator<Term> f
  ) {
    if (this.fun == fun && this.arg == arg && this.a == a && this.b == b) return this;
    return new PAppTerm(fun, arg, a, b).make(f);
  }

  @Override public @NotNull Term descent(@NotNull TermVisitor visitor) {
    return update(
      visitor.term(fun), visitor.term(arg),
      visitor.term(a), visitor.term(b),
      visitor::term
    );
  }

  @Override public @NotNull Term make(@NotNull UnaryOperator<Term> mapper) {
    if (fun instanceof LamTerm(var closure)) return closure.apply(arg);
    if (arg instanceof DimTerm dim) return switch (dim) {
      case I0 -> a;
      case I1 -> b;
    };
    return this;
  }
}
