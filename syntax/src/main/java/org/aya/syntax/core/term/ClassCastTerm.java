// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term;

import kala.collection.immutable.ImmutableSeq;
import kala.function.IndexedFunction;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.def.ClassDefLike;
import org.aya.syntax.core.def.MemberDefLike;
import org.aya.syntax.core.term.marker.BindingIntro;
import org.aya.syntax.core.term.marker.StableWHNF;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
) implements StableWHNF, Term, BindingIntro {
  public ClassCastTerm {
    // forget cannot be empty, I think it is not good because we can make an infinite size term,
    // i.e. fix (\x => cast x [] [])
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
      : ClassCastTerm.make(ref, subterm, remember, forget);
  }

  public static @NotNull ClassCastTerm make(
    @NotNull ClassDefLike ref,
    @NotNull Term subterm,
    @NotNull ImmutableSeq<Closure> remember,
    @NotNull ImmutableSeq<Closure> forget
  ) {
    return new ClassCastTerm(ref, subterm, remember, forget).make();
  }

  /**
   * ClassCastTerm needs not to be {@link org.aya.syntax.core.term.marker.BetaRedex}
   * even it has method `make`,
   * {@link ClassCastTerm#make} intends to merge two {@link ClassCastTerm}s but
   * {@link org.aya.syntax.core.term.marker.BetaRedex#make} intends to unfold.
   */
  public @NotNull ClassCastTerm make() {
    return switch (subterm) {
      case ClassCastTerm subcast ->
        // subcast : SomeClass (this.remember ++ this.forget)
        // subcast.subterm : SomeClass ((this.remember ++ this.forget) ++ subcast.forget)
        // Obviously, if we only remember [this.remember], we forget [this.forget ++ subcast.forget]
        new ClassCastTerm(ref, subcast.subterm, remember, forget.appendedAll(subcast.forget));
      default -> this;
    };
  }

  @Override
  public @NotNull Term descent(@NotNull IndexedFunction<Term, Term> f) {
    return update(f.apply(0, subterm), remember.map(x -> x.descent(f)), forget.map(x -> x.descent(f)));
  }

  public @Nullable Closure get(@NotNull MemberDefLike member) {
    assert ref.equals(member.classRef());
    var idx = member.index();
    if (idx < remember.size()) return remember.get(idx);
    idx = idx - remember.size();
    if (idx < forget.size()) return forget.get(idx);
    return null;
  }
}
