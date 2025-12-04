// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.xtt;

import org.aya.generic.TermVisitor;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;

/// lhs = rhs
public record EqCofTerm(@NotNull Term lhs, @NotNull Term rhs) implements Term {
  public @NotNull EqCofTerm descent(@NotNull TermVisitor visitor) {
    return new EqCofTerm(visitor.term(lhs()), visitor.term(rhs));
  }
}
