// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.SeqLike;
import kala.collection.mutable.MutableList;
import org.aya.core.visitor.BetaExpander;
import org.aya.generic.SortKind;
import org.aya.ref.LocalVar;
import org.aya.util.Arg;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.UnaryOperator;

/**
 * @author re-xyr, kiva, ice1000
 */
public record PiTerm(@NotNull Term.Param param, @NotNull Term body) implements StableWHNF, Term {
  public static @NotNull Term unpi(@NotNull Term term, @NotNull UnaryOperator<Term> fmap, @NotNull MutableList<Param> params) {
    while (fmap.apply(term) instanceof PiTerm(var param, var body)) {
      params.append(param);
      term = body;
    }
    return term;
  }

  public static @Nullable SortTerm max(@NotNull SortTerm domain, @NotNull SortTerm codomain) {var alift = domain.lift();
    var blift = codomain.lift();
    return switch (domain.kind()) {
      case Type -> switch (codomain.kind()) {
        case Type, Set -> new SortTerm(SortKind.Type, Math.max(alift, blift));
        case ISet -> new SortTerm(SortKind.Set, alift);
        case Prop -> codomain;
      };
      case ISet -> switch (codomain.kind()) {
        case ISet -> SortTerm.Set0;
        case Set, Type -> codomain;
        default -> null;
      };
      case Set -> switch (codomain.kind()) {
        case Set, Type -> new SortTerm(SortKind.Set, Math.max(alift, blift));
        case ISet -> new SortTerm(SortKind.Set, alift);
        default -> null;
      };
      case Prop -> switch (codomain.kind()) {
        case Prop, Type -> codomain;
        default -> null;
      };
    };
  }

  public @NotNull LamTerm coe(@NotNull CoeTerm coe, @NotNull LocalVar varI) {
    var u0Var = new LocalVar("u0");
    var vVar = new LocalVar("v");
    var A = new LamTerm(new Param(varI, IntervalTerm.INSTANCE, true), param.type());
    var B = new LamTerm(new Param(varI, IntervalTerm.INSTANCE, true), body);
    var vType = AppTerm.make(A, new Arg<>(FormulaTerm.RIGHT, true));
    var w = AppTerm.make(CoeTerm.coeFillInv(A, coe.restr(), new RefTerm(varI)), new Arg<>(new RefTerm(vVar), true));
    var BSubsted = B.subst(param.ref(), w.rename());
    var wSubsted = w.subst(varI, FormulaTerm.LEFT).rename();
    return new LamTerm(BetaExpander.coeDom(u0Var, coe.type()),
      new LamTerm(new Param(vVar, vType, true),
        AppTerm.make(new CoeTerm(BSubsted, coe.restr()),
          new Arg<>(AppTerm.make(new RefTerm(u0Var), new Arg<>(wSubsted, true)), true))));
  }

  public @NotNull Term substBody(@NotNull Term term) {
    return body.subst(param.ref(), term);
  }

  public @NotNull Term parameters(@NotNull MutableList<@NotNull Param> params) {
    params.append(param);
    var t = body;
    while (t instanceof PiTerm(var p, var b)) {
      params.append(p);
      t = b;
    }
    return t;
  }

  public static @NotNull Term make(@NotNull SeqLike<@NotNull Param> telescope, @NotNull Term body) {
    return telescope.view().foldRight(body, PiTerm::new);
  }
}
