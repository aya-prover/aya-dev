// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableSeq;
import org.aya.core.def.CtorDef;
import org.aya.core.pat.Pat;
import org.aya.core.repr.CodeShape;
import org.aya.core.repr.ShapeRecognition;
import org.aya.generic.Shaped;
import org.aya.util.Arg;
import org.jetbrains.annotations.NotNull;

import java.util.function.IntFunction;
import java.util.function.UnaryOperator;

/**
 * An efficient represent for Nat
 */
public record IntegerTerm(
  @Override int repr,
  @Override @NotNull ShapeRecognition recognition,
  @Override @NotNull DataCall type
) implements StableWHNF, Shaped.Nat<Term>, ConCallLike {
  public IntegerTerm {
    assert repr >= 0;
  }

  @Override
  public @NotNull ConCall.Head head() {
    var ref = repr == 0
      ? ctorRef(CodeShape.GlobalId.ZERO)
      : ctorRef(CodeShape.GlobalId.SUC);

    return new ConCallLike.Head(type.ref(), ref.core.ref, 0, ImmutableSeq.empty());
  }

  @Override
  public @NotNull ImmutableSeq<Arg<Term>> conArgs() {
    if (repr == 0) {
      return ImmutableSeq.empty();
    }

    var ctorTele = head().ref().core.selfTele;
    assert ctorTele.sizeEquals(1);

    return ImmutableSeq.of(new Arg<>(new IntegerTerm(repr - 1, recognition, type), ctorTele.first().explicit()));
  }

  public IntegerTerm update(DataCall type) {
    return type == type() ? this : new IntegerTerm(repr, recognition, type);
  }

  @Override public @NotNull IntegerTerm descent(@NotNull UnaryOperator<Term> f, @NotNull UnaryOperator<Pat> g) {
    return update((DataCall) f.apply(type));
  }

  @Override
  public @NotNull Term constructorForm() {
    // I AM the constructor form.
    return this;
  }

  @Override public @NotNull Term makeZero(@NotNull CtorDef zero) {
    return new ReduceRule.Con(new IntegerOpsTerm.ConRule(zero.ref, recognition, type),
      0, ImmutableSeq.empty(), ImmutableSeq.empty());
  }

  @Override public @NotNull Term makeSuc(@NotNull CtorDef suc, @NotNull Arg<Term> term) {
    return new ReduceRule.Con(new IntegerOpsTerm.ConRule(suc.ref, recognition, type),
      0, ImmutableSeq.empty(), ImmutableSeq.of(term));
  }

  @Override public @NotNull Term destruct(int repr) {
    return new IntegerTerm(repr, this.recognition, this.type);
  }

  @Override
  public @NotNull IntegerTerm map(@NotNull IntFunction<Integer> f) {
    return new IntegerTerm(f.apply(repr), recognition, type);
  }
}
