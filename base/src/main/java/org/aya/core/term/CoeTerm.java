// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import org.aya.core.def.PrimDef;
import org.aya.core.pat.Pat;
import org.aya.util.Arg;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

public record CoeTerm(@NotNull Term type, @NotNull Term r, @NotNull Term s) implements Term {
  public @NotNull CoeTerm update(@NotNull Term type, @NotNull Term r, @NotNull Term s) {
    return type == type() && r == r() && s == s() ? this : new CoeTerm(type, r, s);
  }

  @Override public @NotNull CoeTerm descent(@NotNull UnaryOperator<Term> f, @NotNull UnaryOperator<Pat> g) {
    return update(f.apply(type), f.apply(r), f.apply(s));
  }

  public @NotNull CoeTerm inverse(Term newTy) {
    return new CoeTerm(newTy, s, r);
  }

  /**
   * For parameter and variable names, see Carlo Angiuli's PhD thesis, page 160.
   * <ul>
   *   <li>x ∈ FV(A.type()), x ∈ FV(B)</li>
   *   <li>A.ref() ∈ FV(B)</li>
   * </ul>
   */
  public static @NotNull LamTerm cover(LamTerm.Param x, Param A, Term B, Term newArg, Term r) {
    var innerCover = new LamTerm(x, A.type()).rename();
    var coeRX = new AppTerm(new CoeTerm(innerCover, r, x.toTerm()), new Arg<>(newArg, true));
    return new LamTerm(x, B.subst(A.ref(), coeRX));
  }

  public @NotNull CoeTerm recoe(Term cover) {
    return new CoeTerm(cover, r, s);
  }

  public @NotNull Term family() {
    return PrimDef.familyI2J(type, r, s);
  }
}
