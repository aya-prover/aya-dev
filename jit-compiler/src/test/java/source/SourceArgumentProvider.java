// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package source;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.morphism.ArgumentProvider;
import org.aya.compiler.morphism.JavaExpr;
import org.jetbrains.annotations.NotNull;

public record SourceArgumentProvider(@NotNull ImmutableSeq<String> names) implements ArgumentProvider {
  @Override public @NotNull SourceFreeJavaExpr.BlackBox arg(int nth) {
    return new SourceFreeJavaExpr.BlackBox(names.get(nth));
  }

  record Lambda(@NotNull ImmutableSeq<JavaExpr> captures,
                @NotNull ImmutableSeq<String> names) implements ArgumentProvider.Lambda {
    @Override public @NotNull JavaExpr capture(int nth) { return captures.get(nth); }

    @Override public @NotNull SourceFreeJavaExpr.BlackBox arg(int nth) {
      return new SourceFreeJavaExpr.BlackBox(names.get(nth));
    }
  }
}
