// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.term;

import org.aya.util.Decision;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * @author re-xyr
 */
public record SigmaTerm(boolean co, @NotNull ImmutableSeq<@NotNull Param> params, @NotNull Term body) implements Term {
  @Override @Contract(pure = true) public @NotNull Decision whnf() {
    return Decision.YES;
  }

  @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitSigma(this, p);
  }

  @Override public <P, Q, R> R doAccept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
    return visitor.visitSigma(this, p, q);
  }
}
