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

  @Contract(pure = true) static @NotNull Term
  proj(@NotNull Term of, int ix) {
    return proj(new Proj(of, ix));
  }

  @Contract(pure = true) static @NotNull Term proj(@NotNull Proj proj) {
    if (proj.of instanceof IntroTerm.Tuple tup) {
      assert tup.items().sizeGreaterThanOrEquals(proj.ix) && proj.ix > 0 : proj.of.toDoc(DistillerOptions.debug()).debugRender();
      return tup.items().get(proj.ix - 1);
    }
    return proj;
  }

  record App(@NotNull Term of, @NotNull Arg<@NotNull Term> arg) implements ElimTerm {
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
