// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.SeqLike;
import kala.collection.mutable.MutableList;
import org.aya.core.visitor.BetaExpander;
import org.aya.util.Arg;
import org.aya.ref.LocalVar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author re-xyr, kiva, ice1000
 */
public record PiTerm(@NotNull Term.Param param, @NotNull Term body) implements FormTerm, StableWHNF {
  public static @NotNull Term unpi(@NotNull Term term, @NotNull MutableList<Param> params) {
    while (term instanceof PiTerm(var param, var body)) {
      params.append(param);
      term = body;
    }
    return term;
  }

  public static @Nullable Sort max(@NotNull Sort domain, @NotNull Sort codomain) {
    return switch (domain) {
      case Type(var alift) -> switch (codomain) {
        case Type(var blift) -> new Type(Math.max(alift, blift));
        case Set(var blift) -> new Type(Math.max(alift, blift));
        case ISet b -> new Set(alift);
        case Prop prop -> prop;
      };
      case ISet a -> switch (codomain) {
        case ISet b -> Set.ZERO;
        case Set b -> b;
        case Type b -> b;
        default -> null;
      };
      case Set(var alift) -> switch (codomain) {
        case Set(var blift) -> new Set(Math.max(alift, blift));
        case Type(var blift) -> new Set(Math.max(alift, blift));
        case ISet b -> new Set(alift);
        default -> null;
      };
      case Prop a -> switch (codomain) {
        case Prop b -> b;
        case Type b -> b;
        default -> null;
      };
    };
  }

  public @NotNull LamTerm coe(CoeTerm coe, LocalVar varI) {
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
