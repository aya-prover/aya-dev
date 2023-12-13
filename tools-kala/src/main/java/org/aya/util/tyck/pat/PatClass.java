// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.tyck.pat;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.primitive.ImmutableIntSeq;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public record PatClass<T>(@NotNull T term, @NotNull ImmutableIntSeq cls) {
  public <R> @NotNull PatClass<R> map(Function<T, R> f) {
    return new PatClass<>(f.apply(term), cls);
  }

  public <P> @NotNull ImmutableSeq<P> extract(@NotNull ImmutableSeq<P> seq) {
    return cls.mapToObj(seq::get);
  }
}
