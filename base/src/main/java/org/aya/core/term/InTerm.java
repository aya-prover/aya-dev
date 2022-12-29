// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import org.aya.core.visitor.AyaRestrSimplifier;
import org.jetbrains.annotations.NotNull;

public record InTerm(@NotNull Term phi, @NotNull Term u) implements Term {
  public static @NotNull Term make(@NotNull Term phi, @NotNull Term u) {
    return make(new InTerm(phi, u));
  }

  public static @NotNull Term make(@NotNull InTerm material) {
    if (material.u instanceof OutTerm io) {
      if (AyaRestrSimplifier.INSTANCE.implies(io.phi(), material.phi)) return io.of();
    }
    return material;
  }
}
