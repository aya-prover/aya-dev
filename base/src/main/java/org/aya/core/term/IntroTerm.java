// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableArray;
import kala.collection.immutable.ImmutableMap;
import kala.collection.mutable.DynamicSeq;
import org.aya.api.ref.DefVar;
import org.aya.concrete.stmt.Decl;
import org.aya.core.def.FieldDef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Introduction rules.
 *
 * @author ice1000
 */
public sealed interface IntroTerm extends Term {
  /**
   * @author ice1000
   */
  record Lambda(@NotNull Param param, @NotNull Term body) implements IntroTerm {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitLam(this, p);
    }

    public static @NotNull Term unwrap(@NotNull Term term, @Nullable DynamicSeq<@NotNull Param> params) {
      if (term instanceof Lambda lambda) {
        if (params != null) params.append(lambda.param);
        return unwrap(lambda.body, params);
      } else return term;
    }

    public static @NotNull Term make(@NotNull SeqLike<@NotNull Param> telescope, @NotNull Term body) {
      return telescope.view().foldRight(body, Lambda::new);
    }
  }

  /**
   * @author kiva
   */
  record New(
    @NotNull CallTerm.Struct struct,
    @NotNull ImmutableMap<DefVar<FieldDef, Decl.StructField>, Term> params
  ) implements IntroTerm {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitNew(this, p);
    }
  }

  /**
   * @author re-xyr
   */
  record Tuple(@NotNull ImmutableArray<Term> items) implements IntroTerm {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitTup(this, p);
    }
  }
}
