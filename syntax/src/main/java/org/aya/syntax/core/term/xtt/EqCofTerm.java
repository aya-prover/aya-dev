// Copyright (c) 2020-2026 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.xtt;

import org.aya.generic.TermVisitor;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.marker.StableWHNF;
import org.jetbrains.annotations.NotNull;

/// lhs = rhs
public record EqCofTerm(@NotNull Term lhs, @NotNull Term rhs) implements StableWHNF {
  public @NotNull EqCofTerm descent(@NotNull TermVisitor visitor) {
    var newLhs = visitor.term(lhs);
    var newRhs = visitor.term(rhs);
    if (newLhs == lhs && newRhs == rhs) return this;
    return new EqCofTerm(newLhs, newRhs);
  }
}
