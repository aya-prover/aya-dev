// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free.morphism.source;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.free.ArgumentProvider;
import org.aya.compiler.free.FreeJavaExpr;
import org.jetbrains.annotations.NotNull;

public record SourceArgumentProvider(@NotNull ImmutableSeq<String> names) implements ArgumentProvider {
  @Override public @NotNull SourceFreeJavaExpr.BlackBox arg(int nth) {
    return new SourceFreeJavaExpr.BlackBox(names.get(nth));
  }

  record Lambda(@NotNull ImmutableSeq<FreeJavaExpr> captures,
                @NotNull ImmutableSeq<String> names) implements ArgumentProvider.Lambda {
    @Override public @NotNull FreeJavaExpr capture(int nth) { return captures.get(nth); }

    @Override public @NotNull SourceFreeJavaExpr.BlackBox arg(int nth) {
      return new SourceFreeJavaExpr.BlackBox(names.get(nth));
    }
  }
}
