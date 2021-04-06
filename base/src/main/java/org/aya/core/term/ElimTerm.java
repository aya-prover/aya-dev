// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.term;

import org.aya.api.util.Arg;
import org.aya.util.Decision;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Elimination rules.
 *
 * @author ice1000
 */
public sealed interface ElimTerm extends Term {
  /**
   * @author re-xyr
   */
  record Proj(@NotNull Term tup, int ix) implements ElimTerm {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitProj(this, p);
    }

    @Override public <P, Q, R> R doAccept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
      return visitor.visitProj(this, p, q);
    }

    @Contract(pure = true) @Override public @NotNull Decision whnf() {
      if (tup instanceof IntroTerm.Tuple) return Decision.NO;
      else return tup.whnf();
    }
  }

  record App(@NotNull Term fn, @NotNull Arg<@NotNull Term> arg) implements Term {
    @Contract(pure = true) @Override public @NotNull Decision whnf() {
      if (fn() instanceof IntroTerm.Lambda) return Decision.NO;
      return fn().whnf();
    }

    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitApp(this, p);
    }

    @Override public <P, Q, R> R doAccept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
      return visitor.visitApp(this, p, q);
    }
  }
}
