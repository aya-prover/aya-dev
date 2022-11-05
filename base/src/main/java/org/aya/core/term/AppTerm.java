// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.mutable.MutableList;
import org.aya.generic.Arg;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public record AppTerm(@NotNull Term of, @NotNull Arg<@NotNull Term> arg) implements Elimination {
  @Contract(pure = true) public static @NotNull Term
  make(@NotNull Term f, @NotNull Arg<Term> arg) {
    return make(new AppTerm(f, arg));
  }

  @Contract(pure = true) public static @NotNull Term make(@NotNull AppTerm app) {
    if (app.of() instanceof MetaTerm hole) {
      if (hole.args().sizeLessThan(hole.ref().telescope))
        return new MetaTerm(hole.ref(), hole.ulift(), hole.contextArgs(), hole.args().appended(app.arg()));
    }
    if (app.of() instanceof ErasedTerm erased) {
      // erased.type() can be an ErrorTerm
      if (erased.type() instanceof PiTerm pi) {
        return new ErasedTerm(pi.substBody(app.arg().term()));
      } else {
        return new ErasedTerm(ErrorTerm.typeOf(app), true);
      }
    }
    if (app.of() instanceof LamTerm lam) return make(lam, app.arg());
    return app;
  }

  public static @NotNull Term make(LamTerm lam, @NotNull Arg<Term> arg) {
    var param = lam.param();
    assert arg.explicit() == param.explicit();
    return lam.body().subst(param.ref(), arg.term());
  }

  public static @NotNull Term unapp(@NotNull Term term, MutableList<Arg<@NotNull Term>> args) {
    while (term instanceof AppTerm app) {
      args.append(app.arg);
      term = app.of;
    }
    args.reverse();
    return term;
  }
}
