// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.xtt;

import org.aya.generic.TermVisitor;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.marker.Formation;
import org.aya.syntax.core.term.marker.StableWHNF;
import org.jetbrains.annotations.NotNull;

///
public record PartialTyTerm(@NotNull Term ty, @NotNull Term cof) implements StableWHNF, Formation {
  public @NotNull PartialTyTerm update(@NotNull Term cof, @NotNull Term ty) {
    return cof == cof() && ty == ty() ? this : new PartialTyTerm(ty, cof);
  }

  @Override public @NotNull PartialTyTerm descent(@NotNull TermVisitor visitor) {
    return update(cof().descent(visitor), visitor.term(ty));
  }
}
