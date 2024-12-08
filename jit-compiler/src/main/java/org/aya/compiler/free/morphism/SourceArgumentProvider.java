// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free.morphism;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.free.ArgumentProvider;
import org.jetbrains.annotations.NotNull;

public record SourceArgumentProvider(@NotNull ImmutableSeq<String> names) implements ArgumentProvider {
  @Override
  public @NotNull SourceFreeJava arg(int nth) {
    return new SourceFreeJava(names.get(nth));
  }
}
