// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.stmt.TeleDecl;
import org.aya.core.def.FieldDef;
import org.aya.guest0x0.cubical.Partial;
import org.aya.ref.DefVar;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Introduction rules.
 *
 * @author ice1000
 */
public sealed interface IntroTerm extends Term {
  /**
   * @author ice1000
   */
  record Lambda(@NotNull Param param, @NotNull Term body) implements IntroTerm, StableWHNF {

    public static @NotNull Term unwrap(@NotNull Term term, @NotNull Consumer<@NotNull Param> params) {
      while (term instanceof Lambda lambda) {
        params.accept(lambda.param);
        term = lambda.body;
      }
      return term;
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
    @NotNull ImmutableMap<DefVar<FieldDef, TeleDecl.StructField>, Term> params
  ) implements IntroTerm, StableWHNF {
  }

  /**
   * @author re-xyr
   */
  record Tuple(@NotNull ImmutableSeq<Term> items) implements IntroTerm, StableWHNF {
  }

  /** partial element */
  record PartEl(@NotNull Partial<Term> partial, @NotNull Term rhsType) implements IntroTerm {
  }

  record PathLam(
    @NotNull ImmutableSeq<Term.Param> params,
    @NotNull Term body
  ) implements IntroTerm, StableWHNF {}
}
