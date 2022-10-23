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

        var varI = new LocalVar("i");
        var codom = apply(new ElimTerm.App(coe.type(), new Arg<>(new RefTerm(varI), true)));

        yield switch (codom) {
          case FormTerm.Path path -> throw new InternalException("TODO");
          case FormTerm.Pi pi -> {
            var u0Var = new LocalVar("u0");
            var vVar = new LocalVar("v");
            var A = new IntroTerm.Lambda(new Term.Param(varI, PrimTerm.Interval.INSTANCE, true), pi.param().type()).rename();
            var B = new IntroTerm.Lambda(new Term.Param(varI, PrimTerm.Interval.INSTANCE, true), pi.body());
            var vType = apply(new ElimTerm.App(A, new Arg<>(PrimTerm.Mula.RIGHT, true)));
            var w = coeFillInv(A, coe.restr(), new RefTerm(vVar));
            var BSubsted = B.subst(pi.param().ref(), w);
            var wSusted = w.subst(varI, PrimTerm.Mula.LEFT);
            yield new IntroTerm.Lambda(new Term.Param(u0Var, new ElimTerm.App(coe.type(), new Arg<>(PrimTerm.Mula.LEFT, true)), true),
              new IntroTerm.Lambda(new Term.Param(vVar, vType, true),
                new ElimTerm.App(new PrimTerm.Coe(BSubsted, coe.restr()),
                  new Arg<>(new ElimTerm.App(new RefTerm(u0Var), new Arg<>(wSusted, true)), true))));
          }
          case FormTerm.Sigma sigma -> throw new InternalException("TODO");
          case FormTerm.Type type -> throw new InternalException("TODO");
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
  private static @NotNull Term coeFill(@NotNull Term type, @NotNull Restr<Term> phi, @NotNull Term u0) {
    var varX = new LocalVar("x");

    var cofib = phi.or(new Restr.Cond<>(new RefTerm(varX), false));
    var varY = new LocalVar("y");
    var paramY = new Term.Param(varY, PrimTerm.Interval.INSTANCE, true);
    var xAndY = PrimTerm.Mula.and(new RefTerm(varX), new RefTerm(varY));
    var a = new IntroTerm.Lambda(paramY, new ElimTerm.App(type, new Arg<>(xAndY, true)));

    var coe = new PrimTerm.Coe(a, cofib);
    var coerced = new ElimTerm.App(coe, new Arg<>(u0, true));

    return new IntroTerm.PathLam(ImmutableSeq.of(varX), coerced);
  }

  /**
   * inverts interval in A : I -> Type
   *
   * @param A pi type I -> Type
   * @return inverted A
   */
  private @NotNull Term invertA(@NotNull Term A) {
    if (A instanceof FormTerm.Pi pi) {
      var paramRef = new RefTerm(pi.param().ref());
      var invertedParam = PrimTerm.Mula.inv(paramRef);
      return pi.substBody(invertedParam);
    } else {
      throw new InternalException("expected A : I -> Type");
    }
  }

  // coeInv (A : I -> Type) (phi: I) (u: A 1) : A 0
  private @NotNull Term coeInv(@NotNull Term A, @NotNull Restr<Term> phi, @NotNull Term u) {
    return apply(new ElimTerm.App(new PrimTerm.Coe(invertA(A), phi), new Arg<>(u, true)));
  }

  // coeFillInv
  private @NotNull Term coeFillInv(@NotNull Term type, @NotNull Restr<Term> phi, @NotNull Term u) {
    return coeFill(invertA(type), phi, u);
  }
}
