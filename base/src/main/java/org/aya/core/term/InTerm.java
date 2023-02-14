// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import org.aya.core.pat.Pat;
import org.aya.core.visitor.AyaRestrSimplifier;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

public record InTerm(@NotNull Term phi, @NotNull Term u) implements Term {
  public @NotNull InTerm update(@NotNull Term phi, @NotNull Term u) {
    return phi == phi() && u == u() ? this : new InTerm(phi, u);
  }

  @Override public @NotNull InTerm descent(@NotNull UnaryOperator<Term> f, @NotNull UnaryOperator<Pat> g) {
    return update(f.apply(phi), f.apply(u));
  }

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
