// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.xtt;

import kala.function.IndexedFunction;
import org.aya.syntax.core.term.LamTerm;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.marker.BetaRedex;
import org.jetbrains.annotations.NotNull;

public record PAppTerm(@NotNull Term fun, @NotNull Term arg, @NotNull Term a, @NotNull Term b) implements BetaRedex {
  public @NotNull Term update(@NotNull Term fun, @NotNull Term arg, @NotNull Term a, @NotNull Term b) {
    if (this.fun == fun && this.arg == arg && this.a == a && this.b == b) return this;
    return new PAppTerm(fun, arg, a, b).make();
  }

  @Override public @NotNull Term descent(@NotNull IndexedFunction<Term, Term> f) {
    return update(
      f.apply(0, fun),
      f.apply(0, arg),
      f.apply(0, a),
      f.apply(0, b)
    );
  }

  @Override public @NotNull Term make() {
    if (fun instanceof LamTerm(var closure)) return closure.apply(arg);
    if (arg instanceof DimTerm dim) return switch (dim) {
      case I0 -> a;
      case I1 -> b;
    };
    return this;
  }
}
