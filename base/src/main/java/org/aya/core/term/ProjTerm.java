// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import org.aya.core.visitor.Subst;
import org.aya.distill.AyaDistillerOptions;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * @author re-xyr
 */
public record ProjTerm(@NotNull Term of, int ix) implements Elimination {
  public static @NotNull Subst
  projSubst(@NotNull Term term, int index, ImmutableSeq<Param> telescope) {
    // instantiate the type
    var subst = new Subst(MutableMap.create());
    telescope.view().take(index).forEachIndexed((i, param) ->
      subst.add(param.ref(), new ProjTerm(term, i + 1)));
    return subst;
  }

  @Contract(pure = true) public static @NotNull Term proj(@NotNull Term of, int ix) {
    return proj(new ProjTerm(of, ix));
  }

  @Contract(pure = true) public static @NotNull Term proj(@NotNull ProjTerm proj) {
    if (proj.of instanceof TupTerm tup) {
      assert tup.items().sizeGreaterThanOrEquals(proj.ix) && proj.ix > 0 : proj.of.toDoc(AyaDistillerOptions.debug()).debugRender();
      return tup.items().get(proj.ix - 1);
    }
    return proj;
  }
}
