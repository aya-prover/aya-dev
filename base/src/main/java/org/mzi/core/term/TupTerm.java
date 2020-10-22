package org.mzi.core.term;

import asia.kala.collection.immutable.ImmutableVector;
import org.jetbrains.annotations.NotNull;
import org.mzi.util.Decision;

/**
 * @author re-xyr
 */
public record TupTerm(@NotNull ImmutableVector<Term> items) implements Term {
  @Override
  public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitTup(this, p);
  }

  @Override
  public @NotNull Decision whnf() {
    return Decision.YES;
  }
}
