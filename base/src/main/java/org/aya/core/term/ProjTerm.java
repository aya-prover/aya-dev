// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableSeq;
import org.aya.core.pat.Pat;
import org.aya.core.visitor.Subst;
import org.aya.prettier.AyaPrettierOptions;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

/**
 * @author re-xyr
 */
public record ProjTerm(@NotNull Term of, int ix) implements Elimination {
  public @NotNull ProjTerm update(@NotNull Term of) {
    return of == of() ? this : new ProjTerm(of, ix);
  }

  @Override public @NotNull ProjTerm descent(@NotNull UnaryOperator<Term> f, @NotNull UnaryOperator<Pat> g) {
    return update(f.apply(of));
  }

  public static @NotNull Subst
  projSubst(@NotNull Term term, int index, ImmutableSeq<Param> telescope, Subst subst) {
    // instantiate the type
    telescope.view().take(index).forEachIndexed((i, param) ->
      subst.add(param.ref(), new ProjTerm(term, i + 1)));
    return subst;
  }

  @Contract(pure = true) public static @NotNull Term proj(@NotNull Term of, int ix) {
    return proj(new ProjTerm(of, ix));
  }

  @Contract(pure = true) public static @NotNull Term proj(@NotNull ProjTerm proj) {
    if (proj.of instanceof TupTerm tup) {
      assert tup.items().sizeGreaterThanOrEquals(proj.ix) && proj.ix > 0 : proj.of.toDoc(AyaPrettierOptions.debug()).debugRender();
      return tup.items().get(proj.ix - 1).term();
    }
    return proj;
  }
}
