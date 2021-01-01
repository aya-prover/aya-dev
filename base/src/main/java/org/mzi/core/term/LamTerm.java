// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.core.term;

import org.jetbrains.annotations.NotNull;
import org.mzi.core.Param;
import org.mzi.util.Decision;

/**
 * @author ice1000
 */
public record LamTerm(@NotNull Param param, @NotNull Term body) implements Term {
  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitLam(this, p);
  }

  @Override public <P, Q, R> R accept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
    return visitor.visitLam(this, p, q);
  }

  @Override public @NotNull Decision whnf() {
    return Decision.YES;
  }
}
