// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import org.aya.generic.Arg;
import org.aya.guest0x0.cubical.Restr;
import org.aya.ref.LocalVar;
import org.jetbrains.annotations.NotNull;

import static org.aya.guest0x0.cubical.CofThy.isOne;

public record CoeTerm(@NotNull Term type, @NotNull Restr<Term> restr) implements Term {
  /**
   * <code>coeFill (A: I -> Type) (phi: I) : Path A u (coe A phi u)</code>
   *
   * @param ri the interval abstraction or its inverse
   */
  public static @NotNull Term coeFill(@NotNull Term type, @NotNull Restr<Term> phi, Term ri) {
    var cofib = phi.or(new Restr.Cond<>(ri, false));
    var varY = new LocalVar("y");
    var paramY = new Param(varY, IntervalTerm.INSTANCE, true);
    var xAndY = FormulaTerm.and(ri, new RefTerm(varY));
    var a = new LamTerm(paramY, AppTerm.make(type, new Arg<>(xAndY, true)));

    return new CoeTerm(a, cofib);
  }

  // forward (A: I -> Type) (r: I): A r -> A 1
  private static @NotNull Term forward(@NotNull Term A, @NotNull Term r) {
    var varI = new LocalVar("i");
    var varU = new LocalVar("u");

    var iOrR = FormulaTerm.or(new RefTerm(varI), r);
    var cofib = isOne(r);
    var Ar = AppTerm.make(A, new Arg<>(r, true));
    var AiOrR = AppTerm.make(A, new Arg<>(iOrR, true));
    var lam = new LamTerm(new Param(varI, IntervalTerm.INSTANCE, true), AiOrR);
    var transp = new CoeTerm(lam, cofib);
    var body = AppTerm.make(transp, new Arg<>(new RefTerm(varU), true));
    return new LamTerm(new Param(LocalVar.IGNORED, Ar, true), body);
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
      new Param(i, IntervalTerm.INSTANCE, true),
      AppTerm.make(A, new Arg<>(invertedI, true)));
  }

  // coeInv (A : I -> Type) (phi: I) (u: A 1) : A 0
  private static @NotNull Term coeInv(@NotNull Term A, @NotNull Restr<Term> phi, @NotNull Term u) {
    return AppTerm.make(new CoeTerm(invertA(A), phi), new Arg<>(u, true));
  }

  // coeFillInv
  public static @NotNull Term coeFillInv(@NotNull Term type, @NotNull Restr<Term> phi, @NotNull Term ri) {
    return coeFill(invertA(type), phi, FormulaTerm.inv(ri));
  }
}
