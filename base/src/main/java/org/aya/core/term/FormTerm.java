// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.term;

import org.aya.tyck.sort.Sort;
import org.aya.util.Decision;
import org.glavo.kala.collection.SeqLike;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Formation rules.
 *
 * @author ice1000
 */
public sealed interface FormTerm extends Term {
  @Override @Contract(pure = true) default @NotNull Decision whnf() {
    return Decision.YES;
  }

  /**
   * @author re-xyr, kiva, ice1000
   */
  record Pi(boolean co, @NotNull Term.Param param, @NotNull Term body) implements FormTerm {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitPi(this, p);
    }

    @Override public <P, Q, R> R doAccept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
      return visitor.visitPi(this, p, q);
    }

    public @NotNull Term substBody(@NotNull Term term) {
      return body.subst(param.ref(), term);
    }

    public @NotNull Term parameters(@NotNull Buffer<Term.@NotNull Param> params) {
      params.append(param);
      var t = body;
      while (t instanceof Pi pi) {
        params.append(pi.param);
        t = pi.body;
      }
      return t;
    }

    public static @NotNull Term make(boolean co, @NotNull SeqLike<@NotNull Param> telescope, @NotNull Term body) {
      return telescope.view().reversed().foldLeft(body, (t, p) -> new Pi(co, p, t));
    }
  }

  /**
   * @author re-xyr
   */
  record Sigma(boolean co, @NotNull ImmutableSeq<@NotNull Param> params) implements FormTerm {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitSigma(this, p);
    }

    @Override public <P, Q, R> R doAccept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
      return visitor.visitSigma(this, p, q);
    }
  }

  /**
   * @author ice1000
   */
  record Univ(@NotNull Sort sort) implements FormTerm {
    public static final @NotNull FormTerm.Univ OMEGA = new Univ(Sort.OMEGA);

    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitUniv(this, p);
    }

    @Override public <P, Q, R> R doAccept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
      return visitor.visitUniv(this, p, q);
    }
  }
}
