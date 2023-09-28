// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableSeq;
import org.aya.core.def.CtorDef;
import org.aya.core.pat.Pat;
import org.aya.core.repr.CodeShape;
import org.aya.core.repr.ShapeRecognition;
import org.aya.core.repr.TermShape;
import org.aya.generic.Shaped;
import org.aya.util.Arg;
import org.aya.util.error.InternalException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.UnaryOperator;

public record IntegerTerm(
  @Override int repr,
  @Override @NotNull ShapeRecognition recognition,
  @Override @NotNull DataCall type
) implements StableWHNF, Shaped.Nat<Term> {
  public IntegerTerm update(DataCall type) {
    return type == type() ? this : new IntegerTerm(repr, recognition, type);
  }

  @Override public @NotNull IntegerTerm descent(@NotNull UnaryOperator<Term> f, @NotNull UnaryOperator<Pat> g) {
    return update((DataCall) f.apply(type));
  }

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

  public int construct(@NotNull Term term) {
    if (term instanceof IntegerTerm intTerm) {
      return intTerm.repr;
    }

    if (term instanceof ConCall kon) {
      if (kon.ref() == recognition.captures().get(CodeShape.MomentId.ZERO)) {
        return 0;
      }

      if (kon.ref() == recognition.captures().get(CodeShape.MomentId.SUC)) {
        var inner = kon.conArgs().get(0).term();
        var innerRepr = construct(inner);
        return innerRepr + 1;
      }
    }

    throw new InternalException("Unable to construct an IntegerTerm from " + term);
  }
}
