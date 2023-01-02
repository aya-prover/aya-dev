// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableSeq;
import org.aya.util.Arg;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

public record PAppTerm(
  @NotNull Term of,
  @NotNull ImmutableSeq<Arg<@NotNull Term>> args,
  @NotNull PathTerm cube
) implements Elimination {
  public @NotNull PAppTerm update(@NotNull Term of, @NotNull ImmutableSeq<Arg<Term>> args, @NotNull PathTerm cube) {
    return of == of() && args.sameElements(args(), true) && cube == cube() ? this : new PAppTerm(of, args, cube);
  }

  @Override public @NotNull PAppTerm descent(@NotNull UnaryOperator<@NotNull Term> f) {
    return update(f.apply(of), args.map(arg -> arg.descent(f)), cube.descent(f));
  }

  @SafeVarargs public PAppTerm(@NotNull Term of, @NotNull PathTerm cube, Arg<@NotNull Term> @NotNull ... args) {
    this(of, ImmutableSeq.of(args), cube);
  }
}
