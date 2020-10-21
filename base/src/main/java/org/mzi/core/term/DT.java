package org.mzi.core.term;

import org.jetbrains.annotations.NotNull;
import org.mzi.core.tele.Tele;
import org.mzi.core.tele.Telescopic;
import org.mzi.generic.DTKind;
import org.mzi.util.Decision;

/**
 * A (co)dependent type.
 *
 * @author ice1000
 */
public record DT(
  @NotNull Tele telescope,
  @NotNull DTKind kind
) implements Term, Telescopic {
  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitDT(this, p);
  }

  @Override public @NotNull Decision whnf() {
    return Decision.YES;
  }
}
