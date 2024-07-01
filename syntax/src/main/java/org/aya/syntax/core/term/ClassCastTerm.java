// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term;

import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import kala.function.IndexedFunction;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.def.ClassDefLike;
import org.aya.syntax.core.def.MemberDefLike;
import org.aya.util.MapUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.UnaryOperator;

/**
 * This term is used for subtyping of class, a term {@code x : SomeClass (foo := 114514)} is treated an
 * instance of {@code SomeClass} in user side. In core side, we use {@code cast x [] [(foo := 114514)] : SomeClass}
 * to make {@code x} an instance of {@code SomeClass}, it also store the type information in expression
 * so that the {@link org.aya.syntax.core.term.call.MemberCall} can access them without synthesizing.
 */
public record ClassCastTerm(
  @NotNull ClassDefLike ref,
  @NotNull Term subterm,
  @NotNull ImmutableSeq<Closure> remember,
  @NotNull ImmutableSeq<Closure> forget
) implements Term {
  public ClassCastTerm {
    assert forget.isNotEmpty();
  }

  public @NotNull ClassCastTerm update(
    @NotNull Term subterm,
    @NotNull ImmutableSeq<Closure> remember,
    @NotNull ImmutableSeq<Closure> forget
  ) {
    return this.subterm == subterm
      && this.remember.sameElements(remember, true)
      && this.forget.sameElements(forget, true)
      ? this
      : new ClassCastTerm(ref, subterm, remember, forget);
  }

  @Override
  public @NotNull Term descent(@NotNull IndexedFunction<Term, Term> f) {
    return update(f.apply(0, subterm), remember.map(x -> x.descent(f)), forget.map(x -> x.descent(f)));
  }

  public @Nullable Closure get(@NotNull MemberDefLike member) {
    assert ref == member.classRef();
    var idx = member.index();
    if (idx < remember.size()) return remember.get(idx);
    idx = idx - remember.size();
    if (idx < forget.size()) return forget.get(idx);
    return null;
  }

  public @NotNull Term unwrap(@NotNull UnaryOperator<Term> pre) {
    Term term = this;
    while (term instanceof ClassCastTerm cast) {
      term = pre.apply(cast.subterm);
    }

    return term;
  }
}
