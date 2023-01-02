// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import org.aya.guest0x0.cubical.Restr;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

public record HCompTerm(@NotNull Term type, @NotNull Restr<Term> phi, @NotNull Term u,
                        @NotNull Term u0) implements Term {
  @Override public @NotNull HCompTerm descent(@NotNull UnaryOperator<@NotNull Term> f) {
    throw new UnsupportedOperationException("TODO");
  }
}
