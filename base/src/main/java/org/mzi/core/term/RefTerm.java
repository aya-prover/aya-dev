package org.mzi.core.term;

import org.jetbrains.annotations.NotNull;
import org.mzi.api.ref.Var;
import org.mzi.util.Decision;

/**
 * @author ice1000
 */
public record RefTerm(@NotNull Var var) implements Term {
  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitRef(this, p);
  }

  @Override public @NotNull Decision whnf() {
    return Decision.YES;
  }
}
