// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
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
    if (of instanceof IntroTerm.Tuple tup) {
      assert tup.items().sizeGreaterThanOrEquals(ix) && ix > 0 : of.toDoc(DistillerOptions.debug()).debugRender();
      return tup.items().get(ix - 1);
    }
    return new ElimTerm.Proj(of, ix);
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
