// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.xtt;

import kala.function.IndexedFunction;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.marker.Formation;
import org.aya.syntax.core.term.marker.StableWHNF;
import org.jetbrains.annotations.NotNull;

///
public record PartialTyTerm(@NotNull Term ty, @NotNull DisjCof cof) implements StableWHNF, Formation {
  public @NotNull PartialTyTerm update(@NotNull DisjCof cof, @NotNull Term ty) {
    return cof == cof() && ty == ty() ? this : new PartialTyTerm(ty, cof);
  }

  @Override public @NotNull PartialTyTerm descent(@NotNull IndexedFunction<Term, Term> f) {
    return update(cof().decent(f), f.apply(0,ty));
  }
}
