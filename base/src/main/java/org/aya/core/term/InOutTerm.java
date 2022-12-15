// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import org.aya.core.visitor.AyaRestrSimplifier;
import org.jetbrains.annotations.NotNull;

public record InOutTerm(@NotNull Term phi, @NotNull Term u, @NotNull Kind kind) implements Term {
  public enum Kind {
    In, Out;
    public final @NotNull String fnName = name().toLowerCase() + "S";
  }

  public static @NotNull Term make(@NotNull Term phi, @NotNull Term u, @NotNull Kind kind) {
    return make(new InOutTerm(phi, u, kind));
  }

  public static @NotNull Term make(@NotNull InOutTerm material) {
    if (material.u instanceof InOutTerm io && io.kind != material.kind) {
      if (material.kind == Kind.Out) return io.u;
      if (AyaRestrSimplifier.INSTANCE.implies(io.phi, material.phi)) return io.u;
    }
    return material;
  }
}
