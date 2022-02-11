// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.DynamicSeq;
import kala.collection.mutable.MutableMap;
import org.aya.core.visitor.Substituter;
import org.aya.generic.Arg;
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
    public static @NotNull Substituter.TermSubst
    projSubst(@NotNull Term term, int index, ImmutableSeq<Param> telescope) {
      // instantiate the type
      var subst = new Substituter.TermSubst(MutableMap.create());
      telescope.view().take(index).reversed().forEachIndexed((i, param) ->
        subst.add(param.ref(), new Proj(term, i + 1)));
      return subst;
    }

    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitProj(this, p);
    }
  }

  record App(@NotNull Term of, @NotNull Arg<@NotNull Term> arg) implements ElimTerm {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitApp(this, p);
    }
  }

  static @NotNull Term unapp(@NotNull Term term, DynamicSeq<Arg<@NotNull Term>> args) {
    while (term instanceof ElimTerm.App app) {
      args.append(app.arg);
      term = app.of;
    }
    args.reverse();
    return term;
  }
}
