// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import org.aya.core.pat.Pat;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

public enum IntervalTerm implements StableWHNF, Formation {
  INSTANCE;

  @Override public @NotNull IntervalTerm descent(@NotNull UnaryOperator<Term> f, @NotNull UnaryOperator<Pat> g) {
    return this;
  }
}
