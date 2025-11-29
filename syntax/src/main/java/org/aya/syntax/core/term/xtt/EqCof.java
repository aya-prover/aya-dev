// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.xtt;

import kala.function.IndexedFunction;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;

/// lhs = rhs
public record EqCof(@NotNull Term lhs, @NotNull Term rhs) {
  public @NotNull EqCof descent(@NotNull IndexedFunction<Term, Term> f) {
    return new EqCof(f.apply(0, lhs()), f.apply(0, rhs));
  }
}
