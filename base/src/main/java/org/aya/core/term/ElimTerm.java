// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import org.aya.core.visitor.Subst;
import org.aya.generic.Arg;
import org.aya.util.distill.DistillerOptions;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Elimination rules.
 *
 * @author ice1000
 */
public sealed interface ElimTerm extends Term {
  @Contract(pure = true) static @NotNull Term
  make(@NotNull Term f, @NotNull Arg<Term> arg) {
    return make(new App(f, arg));
  }

  @Contract(pure = true) static @NotNull Term make(@NotNull ElimTerm.App app) {
    if (app.of() instanceof CallTerm.Hole hole) {
      if (hole.args().sizeLessThan(hole.ref().telescope))
        return new CallTerm.Hole(hole.ref(), hole.ulift(), hole.contextArgs(), hole.args().appended(app.arg()));
    }
    if (app.of() instanceof ErasedTerm erased) {
      if (erased.type() instanceof FormTerm.Pi pi) {
        return new ErasedTerm(pi.substBody(app.arg().term()));
      } else {
        return new ErasedTerm(ErrorTerm.typeOf(app));
      }
    }
    if (app.of() instanceof IntroTerm.Lambda lam) return make(lam, app.arg());
    return app;
  }

  static @NotNull Term make(IntroTerm.Lambda lam, @NotNull Arg<Term> arg) {
    var param = lam.param();
    assert arg.explicit() == param.explicit();
    return lam.body().subst(param.ref(), arg.term());
  }

  @NotNull Term of();

  /**
   * @author re-xyr
   */
  record Proj(@NotNull Term of, int ix) implements ElimTerm {
    public static @NotNull Subst
    projSubst(@NotNull Term term, int index, ImmutableSeq<Param> telescope) {
      // instantiate the type
      var subst = new Subst(MutableMap.create());
      telescope.view().take(index).reversed().forEachIndexed((i, param) ->
        subst.add(param.ref(), new Proj(term, i + 1)));
      return subst;
    }
  }

  @Contract(pure = true) static @NotNull Term proj(@NotNull Term of, int ix) {
    return proj(new Proj(of, ix));
  }

  @Contract(pure = true) static @NotNull Term proj(@NotNull Proj proj) {
    if (proj.of instanceof IntroTerm.Tuple tup) {
      assert tup.items().sizeGreaterThanOrEquals(proj.ix) && proj.ix > 0 : proj.of.toDoc(DistillerOptions.debug()).debugRender();
      return tup.items().get(proj.ix - 1);
    }
    if (proj.of instanceof ErasedTerm erased) {
      if (erased.type() instanceof FormTerm.Sigma sigma) {
        return new ErasedTerm(sigma.params().get(proj.ix - 1).type());
      } else {
        return new ErasedTerm(ErrorTerm.typeOf(proj));
      }
    }
    return proj;
  }

  record App(@NotNull Term of, @NotNull Arg<@NotNull Term> arg) implements ElimTerm {
  }

  record PathApp(
    @NotNull Term of,
    @NotNull ImmutableSeq<Arg<@NotNull Term>> args,
    @NotNull FormTerm.Cube cube
  ) implements ElimTerm {
  }

  static @NotNull Term unapp(@NotNull Term term, MutableList<Arg<@NotNull Term>> args) {
    while (term instanceof ElimTerm.App app) {
      args.append(app.arg);
      term = app.of;
    }
    args.reverse();
    return term;
  }
}
