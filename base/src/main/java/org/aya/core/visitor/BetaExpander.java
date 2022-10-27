// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import org.aya.core.term.*;
import org.aya.generic.Arg;
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
      case ElimTerm.PathApp(var of, var args, FormTerm.Cube(var xi, var type, var partial)) -> {
        if (of instanceof ErasedTerm) {
          var ui = args.map(Arg::term);
          yield new ErasedTerm(type.subst(new Subst(xi, ui)));
        }
        if (of instanceof IntroTerm.PathLam lam) {
          var ui = args.map(Arg::term);
          var subst = new Subst(lam.params(), ui);
          yield apply(lam.body().subst(subst));
        }
        yield switch (partial(partial)) {
          case Partial.Split<Term> hap -> new ElimTerm.PathApp(of, args, new FormTerm.Cube(xi, type, hap));
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
        var codom = apply(ElimTerm.make(coe.type(), new Arg<>(new RefTerm(varI), true)));

        yield switch (codom) {
          case FormTerm.Path path -> coe;
          case FormTerm.Pi pi -> {
            var u0Var = new LocalVar("u0");
            var vVar = new LocalVar("v");
            var A = new IntroTerm.Lambda(new Term.Param(varI, PrimTerm.Interval.INSTANCE, true), pi.param().type());
            var B = new IntroTerm.Lambda(new Term.Param(varI, PrimTerm.Interval.INSTANCE, true), pi.body());
            var vType = ElimTerm.make(A, new Arg<>(PrimTerm.Mula.RIGHT, true));
            var w = ElimTerm.make(coeFillInv(A, coe.restr(), new RefTerm(varI)), new Arg<>(new RefTerm(vVar), true));
            var BSubsted = B.subst(pi.param().ref(), w.rename());
            var wSubsted = w.subst(varI, PrimTerm.Mula.LEFT).rename();
            yield new IntroTerm.Lambda(new Term.Param(u0Var, ElimTerm.make(coe.type(), new Arg<>(PrimTerm.Mula.LEFT, true)), true),
              new IntroTerm.Lambda(new Term.Param(vVar, vType, true),
                ElimTerm.make(new PrimTerm.Coe(BSubsted, coe.restr()),
                  new Arg<>(ElimTerm.make(new RefTerm(u0Var), new Arg<>(wSubsted, true)), true))));
          }
          case FormTerm.Sigma sigma -> coe;
          case FormTerm.Type type -> {
            var A = new LocalVar("A");
            yield new IntroTerm.Lambda(new Term.Param(A, type, true), new RefTerm(A));
          }
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
    var Ar = ElimTerm.make(A, new Arg<>(r, true));
    var AiOrR = ElimTerm.make(A, new Arg<>(iOrR, true));
    var lam = new IntroTerm.Lambda(new Term.Param(varI, PrimTerm.Interval.INSTANCE, true), AiOrR);
    var transp = new PrimTerm.Coe(lam, cofib);
    var body = ElimTerm.make(transp, new Arg<>(new RefTerm(varU), true));
    return new IntroTerm.Lambda(new Term.Param(LocalVar.IGNORED, Ar, true), body);
  }

  /**
   * <code>coeFill (A: I -> Type) (phi: I) : Path A u (coe A phi u)</code>
   *
   * @param ri the interval abstraction or its inverse
   */
  private static @NotNull Term coeFill(@NotNull Term type, @NotNull Restr<Term> phi, Term ri) {
    var cofib = phi.or(new Restr.Cond<>(ri, false));
    var varY = new LocalVar("y");
    var paramY = new Term.Param(varY, PrimTerm.Interval.INSTANCE, true);
    var xAndY = PrimTerm.Mula.and(ri, new RefTerm(varY));
    var a = new IntroTerm.Lambda(paramY, ElimTerm.make(type, new Arg<>(xAndY, true)));

    return new PrimTerm.Coe(a, cofib);
  }

  /**
   * inverts interval in A : I -> Type
   *
   * @param A pi type I -> Type
   * @return inverted A
   */
  private static @NotNull Term invertA(@NotNull Term A) {
    var i = new LocalVar("i");
    var invertedI = PrimTerm.Mula.inv(new RefTerm(i));
    return new IntroTerm.Lambda(
      new Term.Param(i, PrimTerm.Interval.INSTANCE, true),
      ElimTerm.make(A, new Arg<>(invertedI, true)));
  }

  // coeInv (A : I -> Type) (phi: I) (u: A 1) : A 0
  private static @NotNull Term coeInv(@NotNull Term A, @NotNull Restr<Term> phi, @NotNull Term u) {
    return ElimTerm.make(new PrimTerm.Coe(invertA(A), phi), new Arg<>(u, true));
  }

  // coeFillInv
  private static @NotNull Term coeFillInv(@NotNull Term type, @NotNull Restr<Term> phi, @NotNull Term ri) {
    return coeFill(invertA(type), phi, PrimTerm.Mula.inv(ri));
  }
}
