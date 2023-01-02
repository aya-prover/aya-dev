// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

public record StringTerm(@NotNull String string) implements StableWHNF {
  @Override public @NotNull StringTerm descent(@NotNull UnaryOperator<@NotNull Term> f) {
    return this;
  }
}
