// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.mzi.core.term;

import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;
import org.mzi.util.Decision;

/**
 * @author ice1000
 */
public record LamTerm(@NotNull Term.Param param, @NotNull Term body) implements Term {
  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitLam(this, p);
  }

  @Override public <P, Q, R> R accept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
    return visitor.visitLam(this, p, q);
  }

  @Override public @NotNull Decision whnf() {
    return Decision.YES;
  }

  public static @NotNull Term make(@NotNull ImmutableSeq<@NotNull Param> telescope, @NotNull Term body) {
    return telescope.reversed().foldLeft(body, (t, p) -> new LamTerm(p, t));
  }
}
