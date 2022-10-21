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
  ) implements LitTerm, Shaped.Nat<Term> {

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
  }

  record ShapedList(
    @Override @NotNull ImmutableSeq<Term> repr,
    @Override @NotNull AyaShape shape,
    @Override @NotNull Term type
    ) implements LitTerm, Shaped.List<Term> {

    @Override
    public @NotNull Term makeNil(@NotNull CtorDef nil, @NotNull Arg<Term> dataArg) {
      return new CallTerm.Con(nil.dataRef, nil.ref(), ImmutableSeq.of(dataArg), 0, ImmutableSeq.empty());
    }

    @Override
    public @NotNull Term makeCons(@NotNull CtorDef cons, @NotNull Arg<Term> dataArg, @NotNull Term value, @NotNull Term last) {
      return new CallTerm.Con(cons.dataRef, cons.ref(), ImmutableSeq.of(dataArg), 0, ImmutableSeq.of(
        new Arg<>(value, true),
        new Arg<>(last, true)
      ));
    }

    @Override
    public @NotNull Term destruct(@NotNull ImmutableSeq<Term> repr) {
      return new ShapedList(repr, shape(), type());
    }
  }
}
