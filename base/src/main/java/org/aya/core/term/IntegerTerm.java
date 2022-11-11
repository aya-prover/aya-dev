// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableSeq;
import org.aya.core.def.CtorDef;
import org.aya.core.repr.ShapeRecognition;
import org.aya.util.Arg;
import org.aya.generic.Shaped;
import org.jetbrains.annotations.NotNull;

public record IntegerTerm(
  @Override int repr,
  @Override @NotNull ShapeRecognition recognition,
  @Override @NotNull DataCall type
) implements StableWHNF, Shaped.Nat<Term> {

  @Override public @NotNull Term makeZero(@NotNull CtorDef zero) {
    return new ConCall(zero.dataRef, zero.ref, ImmutableSeq.empty(), 0, ImmutableSeq.empty());
  }

  @Override public @NotNull Term makeSuc(@NotNull CtorDef suc, @NotNull Arg<Term> term) {
    return new ConCall(suc.dataRef, suc.ref, ImmutableSeq.empty(), 0,
      ImmutableSeq.of(term));
  }

  @Override public @NotNull Term destruct(int repr) {
    return new IntegerTerm(repr, this.recognition, this.type);
  }
}
