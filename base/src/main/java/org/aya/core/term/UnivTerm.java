// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.term;

import org.aya.tyck.sort.Sort;
import org.aya.util.Decision;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public record UnivTerm(@NotNull Sort sort) implements Term {
  public static final /*@NotNull*/ UnivTerm OMEGA = new UnivTerm(Sort.OMEGA);

  @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitUniv(this, p);
  }

  @Override public <P, Q, R> R doAccept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
    return visitor.visitUniv(this, p, q);
  }

  @Override public @NotNull Decision whnf() {
    return Decision.YES;
  }
}
