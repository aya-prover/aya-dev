package org.mzi.core.term;

import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
// TODO: sort system
public record UnivTerm() implements Term {
  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitUniv(this, p);
  }
}
