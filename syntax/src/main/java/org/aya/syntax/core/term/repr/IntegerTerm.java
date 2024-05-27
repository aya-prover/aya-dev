// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.repr;

import kala.collection.immutable.ImmutableSeq;
import kala.function.IndexedFunction;
import org.aya.generic.stmt.Shaped;
import org.aya.syntax.core.def.ConDefLike;
import org.aya.syntax.core.repr.CodeShape;
import org.aya.syntax.core.repr.ShapeRecognition;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.ConCallLike;
import org.aya.syntax.core.term.call.DataCall;
import org.aya.syntax.core.term.call.RuleReducer;
import org.aya.syntax.core.term.marker.StableWHNF;
import org.jetbrains.annotations.NotNull;

import java.util.function.IntUnaryOperator;

/**
 * An efficient represent for Nat
 */
public record IntegerTerm(
  @Override int repr,
  @NotNull ConDefLike zero,
  @NotNull ConDefLike suc,
  @Override @NotNull DataCall type
) implements StableWHNF, Shaped.Nat<Term>, ConCallLike {
  public IntegerTerm {
    assert repr >= 0;
  }

  public IntegerTerm(int repr, @NotNull ShapeRecognition recog, @NotNull DataCall type) {
    this(repr, recog.getCon(CodeShape.GlobalId.ZERO), recog.getCon(CodeShape.GlobalId.SUC), type);
  }

  @Override
  public @NotNull ConCallLike.Head head() {
    return new ConCallLike.Head(repr == 0 ? zero : suc, 0, ImmutableSeq.empty());
  }

  @Override public @NotNull ImmutableSeq<Term> conArgs() {
    if (repr == 0) return ImmutableSeq.empty();
    return ImmutableSeq.of(new IntegerTerm(repr - 1, zero, suc, type));
  }

  @Override public @NotNull Term descent(@NotNull IndexedFunction<Term, Term> f) { return this; }
  @Override public @NotNull Term constructorForm() {
    // I AM the constructor form.
    return this;
  }

  @Override public @NotNull IntegerTerm makeZero() { return map(_ -> 0); }
  @Override public @NotNull Term makeSuc(@NotNull Term term) {
    return new RuleReducer.Con(new IntegerOps.ConRule(suc, makeZero()),
      0, type.args(), ImmutableSeq.of(term));
  }

  @Override public @NotNull Term destruct(int repr) {
    return new IntegerTerm(repr, zero, suc, type);
  }

  @Override public @NotNull IntegerTerm map(@NotNull IntUnaryOperator f) {
    return new IntegerTerm(f.applyAsInt(repr), zero, suc, type);
  }
  @Override public int ulift() { return type.ulift(); }
}
