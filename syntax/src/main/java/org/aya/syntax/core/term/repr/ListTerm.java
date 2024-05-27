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

import java.util.function.UnaryOperator;

public record ListTerm(
  @Override @NotNull ImmutableSeq<Term> repr,
  @NotNull ConDefLike nil,
  @NotNull ConDefLike cons,
  @Override @NotNull DataCall type
) implements StableWHNF, Shaped.List<Term>, ConCallLike {
  public ListTerm(
    @NotNull ImmutableSeq<Term> repr,
    @NotNull ShapeRecognition recog,
    @NotNull DataCall type
  ) {
    this(repr, recog.getCon(CodeShape.GlobalId.NIL), recog.getCon(CodeShape.GlobalId.CONS), type);
  }

  @Override public @NotNull ListTerm makeNil() {
    return new ListTerm(ImmutableSeq.empty(), nil, cons, type);
  }

  @Override public @NotNull Term
  makeCons(@NotNull Term x, @NotNull Term last) {
    return new RuleReducer.Con(new ListOps.ConRule(cons, makeNil()), 0,
      type.args(), ImmutableSeq.of(x, last));
  }

  @Override public @NotNull Term destruct(@NotNull ImmutableSeq<Term> repr) {
    return new ListTerm(repr, nil, cons, type);
  }

  public @NotNull ListTerm map(@NotNull UnaryOperator<ImmutableSeq<Term>> f) {
    return new ListTerm(f.apply(repr), nil, cons, type);
  }

  public @NotNull ListTerm update(@NotNull ImmutableSeq<Term> repr, @NotNull DataCall type) {
    return repr.sameElements(this.repr, true) && type == this.type
      ? this : new ListTerm(repr, nil, cons, type);
  }

  @Override public @NotNull Term descent(@NotNull IndexedFunction<Term, Term> f) {
    return update(repr.map(term -> f.apply(0, term)), (DataCall) f.apply(0, type));
  }

  @Override public @NotNull ConCallLike.Head head() {
    var isNil = repr.isEmpty();
    return new Head(isNil ? nil : cons, 0, ImmutableSeq.of(type.args().getFirst()));
  }

  @Override
  public @NotNull ImmutableSeq<Term> conArgs() {
    return repr.isEmpty() ? ImmutableSeq.empty() : ImmutableSeq.of(repr.getFirst(), destruct(repr.drop(1)));
  }
}
