// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.core.term;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.mzi.core.Tele;
import org.mzi.util.Decision;

/**
 * @author kiva
 */
public interface DT extends Term {
  @NotNull Tele telescope();

  /**
   * @return that if it's a copi or a cosigma.
   */
  boolean co();

  @Override @Contract(pure = true) default @NotNull Decision whnf() {
    return Decision.YES;
  }

  /**
   * A (co)dependent sigma type.
   *
   * @author kiva
   * @apiNote telescope might be empty or longer than one.
   */
  record SigmaTerm(
    @NotNull Tele telescope,
    boolean co
  ) implements DT {
    @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitSigma(this, p);
    }

    @Override public <P, Q, R> R accept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
      return visitor.visitSigma(this, p, q);
    }
  }

  /**
   * A (co)dependent pi-type.
   *
   * @author ice1000
   * @apiNote telescope is guaranteed to be non-empty.
   */
  record PiTerm(
    @NotNull Tele telescope,
    @NotNull Term last,
    boolean co
  ) implements DT {
    @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitPi(this, p);
    }

    @Override public <P, Q, R> R accept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
      return visitor.visitPi(this, p, q);
    }
  }
}
