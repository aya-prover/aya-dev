// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.term;

import org.aya.api.util.Arg;
import org.aya.util.Decision;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public record AppTerm(
  @NotNull Term fn,
  @NotNull Arg<@NotNull Term> arg
) implements Term {
  @Contract(pure = true) @Override public @NotNull Decision whnf() {
    if (fn() instanceof LamTerm) return Decision.NO;
    return fn().whnf();
  }

  @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitApp(this, p);
  }

  @Override public <P, Q, R> R doAccept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
    return visitor.visitApp(this, p, q);
  }
}
