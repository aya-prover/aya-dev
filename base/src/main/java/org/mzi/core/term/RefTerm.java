// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.mzi.core.term;

import org.jetbrains.annotations.NotNull;
import org.mzi.api.ref.Var;
import org.mzi.util.Decision;

/**
 * @author ice1000
 */
public record RefTerm(@NotNull Var var) implements Term {
  @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitRef(this, p);
  }

  @Override public <P, Q, R> R doAccept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
    return visitor.visitRef(this, p, q);
  }

  /**
   * @apiNote This is, theoretically incorrect, because {@link org.mzi.api.ref.DefVar}s can be reduced.
   * However, in those cases it is always in a {@link org.mzi.core.term.AppTerm.FnCall},
   * so here we only care about the {@link org.mzi.ref.LevelVar} and {@link org.mzi.ref.LocalVar} cases,
   * in which the term is a WHNF.
   */
  @Override public @NotNull Decision whnf() {
    return Decision.YES;
  }
}
