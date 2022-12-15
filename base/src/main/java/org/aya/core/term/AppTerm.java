// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.mutable.MutableList;
import org.aya.util.Arg;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public record AppTerm(@NotNull Term of, @NotNull Arg<@NotNull Term> arg) implements Elimination {
  @Contract(pure = true) public static @NotNull Term
  make(@NotNull Term f, @NotNull Arg<Term> arg) {
    return make(new AppTerm(f, arg));
  }

  @Contract(pure = true) public static @NotNull Term make(@NotNull AppTerm material) {
    return switch (material.of()) {
      case MetaTerm(var ref, var contextArgs, var args)
        when args.sizeLessThan(ref.telescope) -> new MetaTerm(ref, contextArgs, args.appended(material.arg()));
      case LamTerm lam -> make(lam, material.arg());
      default -> material;
    };
  }

  public static @NotNull Term make(LamTerm lam, @NotNull Arg<Term> arg) {
    var param = lam.param();
    assert arg.explicit() == param.explicit();
    return lam.body().subst(param.ref(), arg.term());
  }

  public static @NotNull Term unapp(@NotNull Term term, MutableList<Arg<@NotNull Term>> args) {
    while (term instanceof AppTerm(var of, var arg)) {
      args.append(arg);
      term = of;
    }
    args.reverse();
    return term;
  }
}
