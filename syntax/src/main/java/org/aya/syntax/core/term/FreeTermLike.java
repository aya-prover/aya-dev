// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term;

import kala.collection.immutable.ImmutableSeq;
import kala.function.IndexedFunction;
import org.aya.syntax.core.term.marker.TyckInternal;
import org.aya.syntax.ref.LocalVar;
import org.jetbrains.annotations.NotNull;

public sealed interface FreeTermLike extends TyckInternal permits FreeTerm, LetFreeTerm {
  @NotNull LocalVar name();

  @Override default @NotNull Term bindAllFrom(@NotNull ImmutableSeq<LocalVar> vars, int fromDepth) {
    var idx = vars.indexOf(this.name());
    if (idx == -1) return this;

    var realDepth = fromDepth + idx;
    return new LocalTerm(realDepth);
  }
}
