// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import org.aya.core.pat.PatMatcher;
import org.aya.core.term.*;
import org.aya.generic.Arg;
import org.aya.guest0x0.cubical.Partial;
import org.aya.guest0x0.cubical.Restr;
import org.aya.ref.LocalVar;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

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
  static @NotNull Option<Term> tryMatch(@NotNull Term scrutinee, @NotNull ImmutableSeq<Term.Clause> clauses) {
    for (var clause : clauses) {
      var subst = PatMatcher.tryBuildSubst(null, clause.pattern(), scrutinee);
      if (subst.isOk()) {
        return Option.some(clause.body().rename().subst(subst.get()));
      } else if (subst.getErr()) return Option.none();
    }
    return Option.none();
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
      case ElimTerm.Match match -> {
        var result = tryMatch(match.of(), match.clauses());
        yield result.isDefined() ? result.get() : match;
      }
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
        // TODO: coe computation
        yield coe;
      }
      default -> term;
    };
  }
}
