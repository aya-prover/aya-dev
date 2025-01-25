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

  record Two<T>(
    @NotNull T term1, T term2,
    @Override @NotNull ImmutableIntSeq cls
  ) implements PatClass { }

  record One<T>(@NotNull T term, @Override @NotNull ImmutableIntSeq cls) implements PatClass {
    public @NotNull Two<T> pair(T term1) {
      return new Two<>(term1, term, cls);
    }
  }

  record Seq<T>(@NotNull ImmutableSeq<T> term, @Override @NotNull ImmutableIntSeq cls) implements PatClass {
    public @NotNull Seq<T> prepend(T elem) {
      return new Seq<>(term.prepended(elem), cls);
    }

    public @NotNull PatClass.Seq<T> ignoreAbsurd(@Nullable ImmutableIntSeq absurdPrefixCount) {
      if (absurdPrefixCount == null) return this;
      return new Seq<>(term, cls.map(i -> i - absurdPrefixCount.get(i)));
    }
  }
}
