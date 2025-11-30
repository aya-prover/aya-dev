// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.xtt;

import org.aya.generic.TermVisitor;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.annotation.Closed;
import org.aya.syntax.core.def.PrimDef;
import org.aya.syntax.core.term.AppTerm;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.marker.BindingIntro;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

public record CoeTerm(@NotNull Closure type, @NotNull Term r, @NotNull Term s) implements Term, BindingIntro {
  public @NotNull CoeTerm update(@NotNull Closure type, @NotNull Term r, @NotNull Term s) {
    return type == type() && r == r() && s == s() ? this : new CoeTerm(type, r, s);
  }

  @Override public @NotNull Term descent(@NotNull TermVisitor visitor) {
    return update(visitor.closure(type), visitor.term(r), visitor.term(s));
  }

  public @NotNull CoeTerm inverse(Closure newTy) { return new CoeTerm(newTy, s, r); }
  public @NotNull CoeTerm recoe(Closure cover) { return new CoeTerm(cover, r, s); }
  public @NotNull CoeTerm recoe(UnaryOperator<@Closed Term> cover) { return recoe(new Closure.Jit(cover)); }
  public @NotNull Term family() { return PrimDef.familyI2J(type, r, s); }
  public @NotNull Term app(Term x) { return new AppTerm(this, x); }
}
