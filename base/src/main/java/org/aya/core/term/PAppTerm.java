// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableSeq;
import org.aya.util.Arg;
import org.jetbrains.annotations.NotNull;

public record PAppTerm(
  @NotNull Term of,
  @NotNull ImmutableSeq<Arg<@NotNull Term>> args,
  @NotNull PathTerm.Cube cube
) implements Elimination {
  @SafeVarargs public PAppTerm(@NotNull Term of, @NotNull PathTerm.Cube cube, Arg<@NotNull Term> @NotNull ... args) {
    this(of, ImmutableSeq.of(args), cube);
  }
}
