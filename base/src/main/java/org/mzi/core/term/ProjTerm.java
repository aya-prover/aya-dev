// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.mzi.core.term;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.mzi.util.Decision;

/**
 * @author re-xyr
 */
public record ProjTerm(@NotNull Term tup, int ix) implements Term {
  @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitProj(this, p);
  }

  @Override public <P, Q, R> R doAccept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
    return visitor.visitProj(this, p, q);
  }

  @Contract(pure = true) @Override public @NotNull Decision whnf() {
    if (tup instanceof TupTerm) return Decision.NO;
    else return tup.whnf();
  }
}
