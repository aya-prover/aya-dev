// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.tyck.pat;

import java.util.function.Function;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.primitive.ImmutableIntSeq;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record PatClass<T>(@NotNull T term, @NotNull ImmutableIntSeq cls) {
  public <R> @NotNull PatClass<R> map(Function<T, R> f) {
    return new PatClass<>(f.apply(term), cls);
  }

  public <P> @NotNull ImmutableSeq<P> extract(@NotNull ImmutableSeq<P> seq) {
    return cls.mapToObj(seq::get);
  }

  public @NotNull PatClass<T> ignoreAbsurd(@Nullable ImmutableIntSeq absurdPrefixCount) {
    if (absurdPrefixCount == null) return this;
    return new PatClass<>(term, cls.map(i -> i - absurdPrefixCount.get(i)));
  }
}
