// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import org.aya.core.pat.Pat;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

public record MetaPatTerm(@NotNull Pat.Meta ref) implements Term {
  @Override public @NotNull MetaPatTerm descent(@NotNull UnaryOperator<@NotNull Term> f) {
    return this;
  }

  public @NotNull Term inline(@NotNull UnaryOperator<Term> afterwards) {
    var sol = ref.solution().get();
    return sol != null ? afterwards.apply(sol.toTerm()) : this;
  }
}
