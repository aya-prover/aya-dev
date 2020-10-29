// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.core.term;

import org.jetbrains.annotations.NotNull;
import org.mzi.core.Tele;
import org.mzi.util.Decision;

/**
 * A (co)dependent sigma type.
 *
 * @author kiva
 * @apiNote telescope might be empty or longer than one.
 */
public record SigmaTerm(
  @NotNull Tele telescope,
  boolean co
) implements DT {
  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitSigma(this, p);
  }

  @Override public <P, Q, R> R accept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
    return visitor.visitSigma(this, p, q);
  }

  @Override public @NotNull Decision whnf() {
    return Decision.YES;
  }
}
