// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

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
    // TODO[861]: implement type-directed reduction, see
    //  https://discord.com/channels/767397347218423858/767397347218423861/1057874167497756682
    // if (out && material.phi instanceof FormulaTerm(Formula.Lit<?>(var isOne)) && isOne) {
    // }
    return material;
  }
}
