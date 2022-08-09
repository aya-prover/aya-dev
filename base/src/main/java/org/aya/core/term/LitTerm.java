// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableSeq;
import org.aya.core.def.CtorDef;
import org.aya.core.repr.AyaShape;
import org.aya.generic.Arg;
import org.aya.generic.Shaped;
import org.jetbrains.annotations.NotNull;

public sealed interface LitTerm extends Term {
  record ShapedInt(
    @Override int repr,
    @Override @NotNull AyaShape shape,
    @Override @NotNull Term type
  ) implements LitTerm, Shaped.Inductively<Term> {

    @Override public @NotNull Term makeZero(@NotNull CtorDef zero) {
      return new CallTerm.Con(zero.dataRef, zero.ref, ImmutableSeq.empty(), 0, ImmutableSeq.empty());
    }

    @Override public @NotNull Term makeSuc(@NotNull CtorDef suc, @NotNull Term term) {
      return new CallTerm.Con(suc.dataRef, suc.ref, ImmutableSeq.empty(), 0,
        ImmutableSeq.of(new Arg<>(term, true)));
    }

    @Override public @NotNull Term destruct(int repr) {
      return new LitTerm.ShapedInt(repr, this.shape, this.type);
    }

    @Override public @NotNull Term self() {
      return this;
    }
  }
}
