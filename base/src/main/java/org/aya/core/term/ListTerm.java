// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableSeq;
import org.aya.core.def.CtorDef;
import org.aya.core.repr.AyaShape;
import org.aya.util.Arg;
import org.aya.generic.Shaped;
import org.jetbrains.annotations.NotNull;

public record ListTerm(
  @Override @NotNull ImmutableSeq<Term> repr,
  @Override @NotNull AyaShape shape,
  @Override @NotNull Term type
) implements StableWHNF, Shaped.List<Term> {

  @Override
  public @NotNull Term makeNil(@NotNull CtorDef nil, @NotNull Arg<Term> dataArg) {
    return new ConCall(nil.dataRef, nil.ref(), ImmutableSeq.of(dataArg), 0, ImmutableSeq.empty());
  }

  @Override
  public @NotNull Term makeCons(@NotNull CtorDef cons, @NotNull Arg<Term> dataArg, @NotNull Arg<Term> x, @NotNull Arg<Term> last) {
    return new ConCall(cons.dataRef, cons.ref(), ImmutableSeq.of(dataArg), 0,
      ImmutableSeq.of(x, last));
  }

  @Override
  public @NotNull Term destruct(@NotNull ImmutableSeq<Term> repr) {
    return new ListTerm(repr, shape(), type());
  }
}
