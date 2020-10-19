package org.mzi.core.term;

import org.jetbrains.annotations.NotNull;
import org.mzi.api.ref.Ref;

/**
 * @author ice1000
 */
public record RefTerm(@NotNull Ref ref) implements Term {
  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitRef(this, p);
  }
}
