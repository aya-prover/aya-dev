// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableSeq;
import org.aya.core.def.CtorDef;
import org.aya.core.pat.Pat;
import org.aya.core.repr.ShapeRecognition;
import org.aya.util.Arg;
import org.aya.generic.Shaped;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

public record ListTerm(
  @Override @NotNull ImmutableSeq<Term> repr,
  @Override @NotNull ShapeRecognition recognition,
  @Override @NotNull DataCall type
) implements StableWHNF, Shaped.List<Term> {
  public @NotNull ListTerm update(@NotNull DataCall type, @NotNull ImmutableSeq<Term> repr) {
    return type == type() && repr.sameElements(repr(), true) ? this : new ListTerm(repr, recognition, type);
  }

  @Override public @NotNull ListTerm descent(@NotNull UnaryOperator<Term> f, @NotNull UnaryOperator<Pat> g) {
    return update((DataCall) f.apply(type), repr.map(f));
  }

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
    return new ListTerm(repr, recognition, type());
  }
}
