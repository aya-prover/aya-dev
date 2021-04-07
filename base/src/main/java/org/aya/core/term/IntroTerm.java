// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.term;

import org.aya.api.ref.DefVar;
import org.aya.concrete.Decl;
import org.aya.core.def.StructDef;
import org.aya.util.Decision;
import org.glavo.kala.collection.SeqLike;
import org.glavo.kala.collection.immutable.ImmutableMap;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Introduction rules.
 *
 * @author ice1000
 */
public sealed interface IntroTerm extends Term {
  @Override @Contract(pure = true) default @NotNull Decision whnf() {
    return Decision.YES;
  }

  /**
   * @author ice1000
   */
  record Lambda(@NotNull Term.Param param, @NotNull Term body) implements IntroTerm {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitLam(this, p);
    }

    @Override public <P, Q, R> R doAccept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
      return visitor.visitLam(this, p, q);
    }

    public static @NotNull Term make(@NotNull SeqLike<@NotNull Param> telescope, @NotNull Term body) {
      return telescope.view().reversed().foldLeft(body, (t, p) -> new Lambda(p, t));
    }
  }

  /**
   * @author kiva
   */
  record New(
    @NotNull ImmutableMap<DefVar<StructDef.Field, Decl.StructField>, Term> params
  ) implements IntroTerm {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitNew(this, p);
    }

    @Override public <P, Q, R> R doAccept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
      return visitor.visitNew(this, p, q);
    }
  }

  /**
   * @author re-xyr
   */
  record Tuple(@NotNull ImmutableSeq<Term> items) implements IntroTerm {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitTup(this, p);
    }

    @Override public <P, Q, R> R doAccept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
      return visitor.visitTup(this, p, q);
    }
  }

}
