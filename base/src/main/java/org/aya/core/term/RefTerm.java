// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.term;

import org.aya.api.ref.DefVar;
import org.aya.api.ref.LocalVar;
import org.aya.ref.LevelVar;
import org.aya.util.Decision;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public record RefTerm(@NotNull LocalVar var) implements Term {
  @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitRef(this, p);
  }

  @Override public <P, Q, R> R doAccept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
    return visitor.visitRef(this, p, q);
  }

  /**
   * @apiNote This is, theoretically incorrect, because {@link DefVar}s can be reduced.
   * However, in those cases it is always in a {@link CallTerm.Fn},
   * so here we only care about the {@link LevelVar} and {@link LocalVar} cases,
   * in which the term is a WHNF.
   */
  @Override public @NotNull Decision whnf() {
    return Decision.YES;
  }
}
