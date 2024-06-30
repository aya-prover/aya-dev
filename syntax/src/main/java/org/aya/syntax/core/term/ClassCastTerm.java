// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term;

import kala.collection.immutable.ImmutableMap;
import kala.collection.mutable.MutableMap;
import kala.function.IndexedFunction;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.def.MemberDefLike;
import org.aya.util.MapUtil;
import org.jetbrains.annotations.NotNull;

/**
 * This term is used for subtyping of class, a term {@code x : SomeClass (foo := 114514)} is treated an
 * instance of {@code SomeClass} in user side. In core side, we use {@code cast x (foo := 114514) : SomeClass}
 * to make {@code x} an instance of {@code SomeClass}, it also store the type information in expression
 * so that the {@link org.aya.syntax.core.term.call.MemberCall} can access them without synthesizing.
 */
public record ClassCastTerm(
  @NotNull Term subterm,
  @NotNull ImmutableMap<MemberDefLike, Closure> restriction
) implements Term {
  public @NotNull ClassCastTerm update(@NotNull Term subterm, @NotNull ImmutableMap<MemberDefLike, Closure> restriction) {
    return this.subterm == subterm
      && MapUtil.sameElements(this.restriction, restriction)
      ? this
      : new ClassCastTerm(subterm, restriction);
  }

  @Override
  public @NotNull Term descent(@NotNull IndexedFunction<Term, Term> f) {
    return update(f.apply(0, subterm), ImmutableMap.from(MutableMap.from(restriction).edit()
      .replaceAll((_, v) -> v.descent(f))
      .done()));
  }
}
