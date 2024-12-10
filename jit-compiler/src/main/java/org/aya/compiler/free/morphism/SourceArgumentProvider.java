// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free.morphism;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.free.ArgumentProvider;
import org.aya.compiler.free.FreeJava;
import org.jetbrains.annotations.NotNull;

public record SourceArgumentProvider(@NotNull ImmutableSeq<String> names) implements ArgumentProvider {
  @Override
  public @NotNull SourceFreeJava arg(int nth) {
    return new SourceFreeJava(names.get(nth));
  }

  record Lambda(@NotNull ImmutableSeq<String> captures,
                @NotNull ImmutableSeq<String> names) implements ArgumentProvider.Lambda {
    @Override
    public @NotNull FreeJava capture(int nth) {
      return new SourceFreeJava(captures.get(nth));
    }

    @Override
    public @NotNull FreeJava arg(int nth) {
      return new SourceFreeJava(names.get(nth));
    }
  }
}
