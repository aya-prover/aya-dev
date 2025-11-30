// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.xtt;

import org.aya.generic.TermVisitor;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.annotation.Closed;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.marker.BindingIntro;
import org.aya.syntax.core.term.marker.Formation;
import org.aya.syntax.core.term.marker.StableWHNF;
import org.jetbrains.annotations.NotNull;

/**
 * <code>PathP (x. A) a b</code>
 */
public record EqTerm(Closure A, Term a, Term b) implements Formation, StableWHNF, BindingIntro {
  public @NotNull EqTerm update(Closure A, Term a, Term b) {
    if (this.A == A && this.a == a && this.b == b) return this;
    return new EqTerm(A, a, b);
  }

  public @NotNull Term appA(@Closed @NotNull Term arg) { return A.apply(arg); }

  public @NotNull Term makePApp(@NotNull Term fun, @NotNull Term arg) {
    return new PAppTerm(fun, arg, a, b).make();
  }

  @Override public @NotNull Term descent(@NotNull TermVisitor visitor) {
    return update(visitor.closure(A), visitor.term(a), visitor.term(b));
  }
}
