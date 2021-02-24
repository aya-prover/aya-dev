// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.mzi.core.term;

import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.mzi.util.Decision;

/**
 * @author re-xyr, kiva, ice1000
 */
public record PiTerm(boolean co, @NotNull Term.Param param, @NotNull Term body) implements Term {
  @Override @Contract(pure = true) public @NotNull Decision whnf() {
    return Decision.YES;
  }

  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitPi(this, p);
  }

  @Override public <P, Q, R> R accept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
    return visitor.visitPi(this, p, q);
  }

  public static @NotNull Term make(boolean co, @NotNull ImmutableSeq<@NotNull Param> telescope, @NotNull Term body) {
    return telescope.reversed().foldLeft(body, (t, p) -> new PiTerm(co, p, t));
  }
}
