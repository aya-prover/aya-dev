// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import org.aya.core.visitor.AyaRestrSimplifier;
import org.aya.guest0x0.cubical.Restr;
import org.aya.ref.LocalVar;
import org.aya.util.Arg;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

public record CoeTerm(@NotNull Term type, @NotNull Restr<Term> restr) implements Term {
  public @NotNull CoeTerm update(@NotNull Term type, @NotNull Restr<Term> restr) {
    return type == type() && restr == restr() ? this
      : new CoeTerm(type, AyaRestrSimplifier.INSTANCE.normalizeRestr(restr));
  }

  @Override public @NotNull CoeTerm descent(@NotNull UnaryOperator<@NotNull Term> f) {
    return update(f.apply(type), restr.map(f));
  }

  /**
   * <code>coeFill (A : I -> Type) (phi : I) : Pi (u : A 0) -> Path A u (coe A phi u)</code>
   *
   * @param ri the interval abstraction or its inverse
   */
  public static @NotNull Term coeFill(@NotNull Term type, @NotNull Restr<Term> phi, Term ri) {
    var cofib = phi.or(new Restr.Cond<>(ri, false));
    var varY = new LocalVar("y");
    var paramY = new LamTerm.Param(varY, true);
    var xAndY = FormulaTerm.and(ri, new RefTerm(varY));
    var a = new LamTerm(paramY, AppTerm.make(type, new Arg<>(xAndY, true)));

    return new CoeTerm(a, cofib);
  }

  // forward (A : I -> Type) (r : I) : A r -> A 1
  private static @NotNull Term forward(@NotNull Term A, @NotNull Term r) {
    var varI = new LocalVar("i");
    var varU = new LocalVar("u");

    var iOrR = FormulaTerm.or(new RefTerm(varI), r);
    var cofib = AyaRestrSimplifier.INSTANCE.isOne(r);
    var AiOrR = AppTerm.make(A, new Arg<>(iOrR, true));
    var lam = new LamTerm(new LamTerm.Param(varI, true), AiOrR);
    var transp = new CoeTerm(lam, cofib);
    var body = AppTerm.make(transp, new Arg<>(new RefTerm(varU), true));
    return new LamTerm(new LamTerm.Param(LocalVar.IGNORED, true), body);
  }

  /**
   * inverts interval in A : I -> Type
   *
   * @param A pi type I -> Type
   * @return inverted A
   */
  private static @NotNull Term invertA(@NotNull Term A) {
    var i = new LocalVar("i");
    var invertedI = FormulaTerm.inv(new RefTerm(i));
    return new LamTerm(
      new LamTerm.Param(i, true),
      AppTerm.make(A, new Arg<>(invertedI, true)));
  }

  // coeInv (A : I -> Type) (phi : I) : A 1 -> A 0
  public static @NotNull Term coeInv(@NotNull Term A, @NotNull Restr<Term> phi) {
    return new CoeTerm(invertA(A), phi);
  }

  // coeInvFill (A : I -> Type) (phi : I) : Pi (u : A 1) -> Path A u (coeInv A phi u)
  public static @NotNull Term coeFillInv(@NotNull Term type, @NotNull Restr<Term> phi, @NotNull Term ri) {
    return coeFill(invertA(type), phi, FormulaTerm.inv(ri));
  }
}
