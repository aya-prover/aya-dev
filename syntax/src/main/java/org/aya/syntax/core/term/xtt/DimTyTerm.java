// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.xtt;

import kala.function.IndexedFunction;
import org.aya.syntax.core.annotation.Closed;
import org.aya.syntax.core.term.Param;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.marker.Formation;
import org.aya.syntax.core.term.marker.StableWHNF;
import org.jetbrains.annotations.NotNull;

@Closed
public enum DimTyTerm implements Formation, StableWHNF {
  INSTANCE;

  public static Param param(String r) {
    return new Param(r, DimTyTerm.INSTANCE, true);
  }

  @Override public @NotNull DimTyTerm descent(@NotNull IndexedFunction<Term, Term> f) {
    return this;
  }
}
