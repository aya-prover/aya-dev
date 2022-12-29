// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import org.aya.guest0x0.cubical.Partial;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 * @see InTerm
 */
public record OutTerm(@NotNull Term phi, @NotNull Term partial, @NotNull Term of) implements Elimination {
  public static @NotNull Term make(@NotNull Term phi, @NotNull Term partial, @NotNull Term u) {
    return make(new OutTerm(phi, partial, u));
  }

  public static @NotNull Term make(@NotNull OutTerm material) {
    if (material.of instanceof InTerm inS) return inS.u();
    if (material.partial instanceof PartialTerm(Partial.Const<Term>(var u), var rhs)) return u;
    return material;
  }
}
