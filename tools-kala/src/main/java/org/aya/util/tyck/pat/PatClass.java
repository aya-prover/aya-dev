// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.tyck.pat;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.primitive.ImmutableIntSeq;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PatClass {
  @NotNull ImmutableIntSeq cls();

  default <P> @NotNull ImmutableSeq<P> extract(@NotNull ImmutableSeq<P> seq) {
    return cls().mapToObj(seq::get);
  }

  record Two<A, B>(
    @NotNull A term1, A term2,
    @NotNull B pat1, B pat2,
    @Override @NotNull ImmutableIntSeq cls
  ) implements PatClass { }

  record One<A, B>(@NotNull A term, @NotNull B pat, @Override @NotNull ImmutableIntSeq cls) implements PatClass {
    public @NotNull Two<A, B> pair(One<A, B> one) { return new Two<>(one.term, term, one.pat, pat, cls); }
  }

  record Seq<A, B>(
    @NotNull ImmutableSeq<A> term, @NotNull ImmutableSeq<B> pat,
    @Override @NotNull ImmutableIntSeq cls
  ) implements PatClass {
    public Seq(@NotNull ImmutableIntSeq cls) {
      this(ImmutableSeq.empty(), ImmutableSeq.empty(), cls);
    }
    public @NotNull Seq<A, B> prepend(One<A, B> one) {
      return new Seq<>(term.prepended(one.term), pat.prepended(one.pat), cls);
    }

    public @NotNull PatClass.Seq<A, B> ignoreAbsurd(@Nullable ImmutableIntSeq absurdPrefixCount) {
      if (absurdPrefixCount == null) return this;
      return new Seq<>(term, pat, cls.map(i -> i - absurdPrefixCount.get(i)));
    }
  }
}
