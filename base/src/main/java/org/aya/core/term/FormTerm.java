// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.DynamicSeq;
import org.aya.core.sort.Sort;
import org.aya.generic.Constants;
import org.aya.generic.Level;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

/**
 * Formation rules.
 *
 * @author ice1000
 */
public sealed interface FormTerm extends Term {
  /**
   * @author re-xyr, kiva, ice1000
   */
  record Pi(@NotNull Term.Param param, @NotNull Term body) implements FormTerm {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitPi(this, p);
    }

    public @NotNull Term substBody(@NotNull Term term) {
      return body.subst(param.ref(), term);
    }

    public @NotNull Term parameters(@NotNull DynamicSeq<Term.@NotNull Param> params) {
      params.append(param);
      var t = body;
      while (t instanceof Pi pi) {
        params.append(pi.param);
        t = pi.body;
      }
      return t;
    }

    public static @NotNull Term make(@NotNull SeqLike<@NotNull Param> telescope, @NotNull Term body) {
      return telescope.view().foldRight(body, Pi::new);
    }
  }

  static @NotNull Term unpi(@NotNull Term term, @NotNull DynamicSeq<Term.Param> params) {
    while (term instanceof Pi pi) {
      params.append(pi.param);
      term = pi.body;
    }
    return term;
  }

  /**
   * @author re-xyr
   */
  record Sigma(@NotNull ImmutableSeq<@NotNull Param> params) implements FormTerm {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitSigma(this, p);
    }
  }

  static @NotNull Univ freshUniv(@NotNull SourcePos pos) {
    return new Univ(freshSort(pos));
  }

  static @NotNull Sort freshSort(@NotNull SourcePos pos) {
    return new Sort(new Level.Reference<>(new Sort.LvlVar(Constants.randomName(pos), pos)));
  }

  /**
   * @author ice1000
   */
  record Univ(@NotNull Sort sort) implements FormTerm {
    public static final @NotNull FormTerm.Univ ZERO = new Univ(new Sort(new Level.Constant<>(0)));

    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitUniv(this, p);
    }

  }
}
