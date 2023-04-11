// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import org.aya.core.pat.Pat;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

/**
 * @author kiva
 */
public record NewTerm(@NotNull ClassCall inner) implements StableWHNF {
  public @NotNull NewTerm update(@NotNull ClassCall classCall) {
    if (classCall == inner) return this;
    return new NewTerm(classCall);
  }

  @Override public @NotNull NewTerm descent(@NotNull UnaryOperator<Term> f, @NotNull UnaryOperator<Pat> g) {
    return update((ClassCall) f.apply(inner));
  }
}
