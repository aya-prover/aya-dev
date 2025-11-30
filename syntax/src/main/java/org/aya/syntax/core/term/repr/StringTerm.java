// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.repr;

import org.aya.generic.TermVisitor;
import org.aya.syntax.core.term.marker.StableWHNF;
import org.jetbrains.annotations.NotNull;

public record StringTerm(@NotNull String string) implements StableWHNF {
  @Override public @NotNull StringTerm descent(@NotNull TermVisitor visitor) {
    return this;
  }
}
