// Copyright (c) 2020-2026 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.xtt;

import org.aya.generic.TermVisitor;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.PrimCall;
import org.jetbrains.annotations.NotNull;


// hcomp {A : Type} (φ : F) (u : (i : I) -> Partial (φ ∧f (i =f 0)) A) : A

public record HCompTerm(@NotNull Term type, @NotNull Term cof, @NotNull Term face) implements Term {
  public @NotNull HCompTerm update(@NotNull Term type, @NotNull Term cof, @NotNull Term face) {
    return type() == type && cof() == cof && face() == face ? this : new HCompTerm(type, cof, face);
  }

  @Override
  public @NotNull Term descent(@NotNull TermVisitor visitor) {
    return update(visitor.term(type), visitor.term(cof), visitor.term(face));
  }
}
