// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.xtt;

import kala.function.IndexedFunction;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.def.PrimDef;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;

public record CoeTerm(@NotNull Closure type, @NotNull Term r, @NotNull Term s) implements Term {
  public @NotNull CoeTerm update(@NotNull Closure type, @NotNull Term r, @NotNull Term s) {
    return type == type() && r == r() && s == s() ? this : new CoeTerm(type, r, s);
  }

  @Override public @NotNull Term descent(@NotNull IndexedFunction<Term, Term> f) {
    return update(type.descent(f), f.apply(0, r), f.apply(0, s));
  }

  public @NotNull CoeTerm inverse(Closure newTy) {
    return new CoeTerm(newTy, s, r);
  }

  public @NotNull CoeTerm recoe(Closure cover) {
    return new CoeTerm(cover, r, s);
  }
  public @NotNull Term family() { return PrimDef.familyI2J(type, r, s); }
}
