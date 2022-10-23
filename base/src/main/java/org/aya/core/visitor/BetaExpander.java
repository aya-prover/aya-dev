// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import kala.collection.immutable.ImmutableSeq;
import org.aya.core.term.*;
import org.aya.generic.Arg;
import org.aya.generic.util.InternalException;
import org.aya.guest0x0.cubical.Partial;
import org.aya.guest0x0.cubical.Restr;
import org.aya.ref.LocalVar;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

import static org.aya.guest0x0.cubical.CofThy.isOne;

/**
 * We think of all cubical reductions as beta reductions.
 *
 * @author wsx
 * @see DeltaExpander
 */
public interface BetaExpander extends EndoFunctor {
  private static @NotNull Partial<Term> partial(@NotNull Partial<Term> partial) {
    return partial.flatMap(Function.identity());
  }
  static @NotNull Term simplFormula(@NotNull PrimTerm.Mula mula) {
    return Restr.formulae(mula.asFormula(), PrimTerm.Mula::new);
  }
  static @NotNull FormTerm.PartTy partialType(@NotNull FormTerm.PartTy ty) {
    return new FormTerm.PartTy(ty.type(), ty.restr().normalize());
  }
  @Override default @NotNull Term post(@NotNull Term term) {
    return switch (term) {
      case PrimTerm.Mula mula -> simplFormula(mula);
      case FormTerm.PartTy ty -> partialType(ty);
      case RefTerm.MetaPat metaPat -> metaPat.inline();
      case ElimTerm.App app -> {
        var result = ElimTerm.make(app);
        yield result == term ? result : apply(result);
      }
      case ElimTerm.Proj proj -> ElimTerm.proj(proj);
      case ElimTerm.PathApp app -> {
        if (app.of() instanceof IntroTerm.PathLam lam) {
          var ui = app.args().map(Arg::term);
          var subst = new Subst(lam.params(), ui);
          yield apply(lam.body().subst(subst));
        }
        yield switch (partial(app.cube().partial())) {
          case Partial.Split<Term> hap -> new ElimTerm.PathApp(app.of(), app.args(), new FormTerm.Cube(
            app.cube().params(), app.cube().type(), hap));
          case Partial.Const<Term> sad -> sad.u();
        };
      }
      case IntroTerm.PartEl partial -> new IntroTerm.PartEl(partial(partial.partial()), partial.rhsType());
      case PrimTerm.Coe coe -> {
        if (coe.restr() instanceof Restr.Const<Term> c && c.isOne()) {
          var var = new LocalVar("x");
          var param = new Term.Param(var, ElimTerm.make(coe.type(), new Arg<>(PrimTerm.Mula.LEFT, true)), true);
          yield new IntroTerm.Lambda(param, new RefTerm(var));
        }

        var A = apply(new ElimTerm.App(coe.type(), new Arg<>(new RefTerm(new LocalVar("x")), true)));

        yield switch (A) {
          case FormTerm.Path path -> null;
          case FormTerm.Pi pi -> null;
          case FormTerm.Sigma sigma -> null;
          case FormTerm.Type type -> null;
          default -> coe;
        };
      }
      default -> term;
    };
  }

  // forward (A: I -> Type) (r: I): A r -> A 1
  private static @NotNull Term forward(@NotNull Term A, @NotNull Term r) {
    var varI = new LocalVar("i");
    var varU = new LocalVar("u");

    var iOrR = PrimTerm.Mula.or(new RefTerm(varI), r);
    var cofib = isOne(r);
    var Ar = new ElimTerm.App(A, new Arg<>(r, true));
    var AiOrR = new ElimTerm.App(A, new Arg<>(iOrR, true));
    var lam = new IntroTerm.Lambda(new Term.Param(varI, PrimTerm.Interval.INSTANCE, true), AiOrR);
    var transp = new PrimTerm.Coe(lam, cofib);
    var body = new ElimTerm.App(transp, new Arg<>(new RefTerm(varU), true));
    return new IntroTerm.Lambda(new Term.Param(LocalVar.IGNORED, Ar, true), body);
  }

  // coeFill (A: I -> Type) (phi: I) (u0: A 0) : Path A u (coe A phi u)
  private static @NotNull Term coeFill(@NotNull Term type, @NotNull Term phi, @NotNull Term u0) {
    var varX = new LocalVar("x");

    var cofib = PrimTerm.Mula.or(phi, PrimTerm.Mula.inv(new RefTerm(varX)));
    var varY = new LocalVar("y");
    var paramY = new Term.Param(varY, PrimTerm.Interval.INSTANCE, true);
    var xAndY = PrimTerm.Mula.and(new RefTerm(varX), new RefTerm(varY));
    var a = new IntroTerm.Lambda(paramY, new ElimTerm.App(type, new Arg<>(xAndY, true)));

    var coe = new PrimTerm.Coe(a, isOne(cofib));
    var coerced = new ElimTerm.App(coe, new Arg<>(u0, true));

    return new IntroTerm.PathLam(ImmutableSeq.of(varX), coerced);
  }

  // coeInv (A : I -> Type) (phi: I) (u: A 1) : A 0
  private static @NotNull Term coeInv(@NotNull Term A, @NotNull Term phi, @NotNull Term u) {
    throw new InternalException("TODO");
  }

  private static @NotNull Term coeFillInv(@NotNull Term A, @NotNull Term phi, @NotNull Term u) {
    throw new InternalException("TODO");
  }
}
