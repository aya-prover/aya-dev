package org.mzi.core.term;

import org.jetbrains.annotations.NotNull;
import org.mzi.core.tele.Tele;
import org.mzi.core.tele.Telescopic;
import org.mzi.util.Decision;

/**
 * A (co)dependent type.
 *
 * @author ice1000
 */
public record DT(
  @NotNull Tele telescope,
  @NotNull Kind kind
) implements Term, Telescopic {
  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitPi(this, p);
  }

  @Override public @NotNull Decision whnf() {
    return Decision.YES;
  }

  public enum Kind {
    Pi(true, true), Sigma(false, true),
    Copi(true, false), Cosigma(false, false);

    public final boolean function, forward;

    Kind(boolean function, boolean forward) {
      this.function = function;
      this.forward = forward;
    }
  }
}
