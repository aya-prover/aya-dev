// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

public final class IntervalTerm implements StableWHNF, Formation {
  public static final IntervalTerm INSTANCE = new IntervalTerm();

  @Override public @NotNull IntervalTerm descent(@NotNull UnaryOperator<@NotNull Term> f) {
    return this;
  }

  private IntervalTerm() {

  }
}
