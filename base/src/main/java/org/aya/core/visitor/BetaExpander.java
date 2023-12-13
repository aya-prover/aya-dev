// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import org.aya.core.term.*;
import org.aya.guest0x0.cubical.Formula;
import org.aya.guest0x0.cubical.Partial;
import org.aya.ref.LocalVar;
import org.aya.util.Arg;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.function.UnaryOperator;

/**
 * We think of all cubical reductions as beta reductions.
 *
 * @author wsx
 * @see DeltaExpander
 */
public interface BetaExpander extends EndoTerm {
  private @NotNull Partial<Term> partial(@NotNull Partial<Term> partial) {
    return AyaRestrSimplifier.INSTANCE.mapPartial(partial, UnaryOperator.identity());
  }

  @Override default @NotNull Term post(@NotNull Term term) {
    return switch (term) {
      case FormulaTerm mula -> mula.simpl();
      case PartialTyTerm ty -> ty.normalizeRestr();
      case MetaPatTerm metaPat -> metaPat.inline(this);
      case MetaLitTerm lit -> lit.inline();
      case AppTerm app -> {
        var result = AppTerm.make(app);
        yield result == term ? result : apply(result);
      }
      case InTerm io -> {
        var result = InTerm.make(io);
        yield result == term ? result : apply(result);
      }
      case ProjTerm proj -> ProjTerm.proj(proj);
      case MatchTerm match -> {
        var result = match.tryMatch();
        yield result.isDefined() ? result.get() : match;
      }
      case PAppTerm(var of, var args, PathTerm(var xi, var type, var partial)) -> {
        if (of instanceof PLamTerm lam) {
          var ui = args.map(Arg::term);
          var subst = new Subst(lam.params(), ui);
          yield apply(lam.body().subst(subst));
        }
        yield switch (partial(partial)) {
          case Partial.Split<Term> hap -> new PAppTerm(of, args, new PathTerm(xi, type, hap));
          case Partial.Const(var sad) -> sad;
        };
      }
      case PartialTerm(var partial, var rhsTy) -> new PartialTerm(partial(partial), rhsTy);
      // TODO[coe]: temporary hack
      case CoeTerm(
        _,
        FormulaTerm(Formula.Lit(var r)),
        FormulaTerm(Formula.Lit(var s))
      ) when r == s -> identity("x");
      case CoeTerm(_, RefTerm(var r), RefTerm(var s)) when r == s -> identity("x");
      case CoeTerm coe -> {
        var varI = new LamTerm.Param(new LocalVar("i"), true);
        var codom = apply(AppTerm.make(coe.type(), varI.toArg()));

        yield switch (codom) {
          case PathTerm _ -> term;
          case PiTerm pi -> pi.coe(coe, varI);
          case SigmaTerm sigma -> sigma.coe(coe, varI);
          case DataCall data when data.args().isEmpty() -> identity(String.valueOf(data.ref()
            .name().chars()
            .filter(Character::isAlphabetic)
            .findFirst()).toLowerCase(Locale.ROOT));
          case SortTerm _ -> identity("A");
          default -> term;
        };
      }
      default -> term;
    };
  }
  static @NotNull Term identity(@NotNull String x) {
    var param = new LamTerm.Param(new LocalVar(x), true);
    return new LamTerm(param, param.toTerm());
  }
}
