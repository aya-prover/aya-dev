package org.mzi.core.term;

import org.jetbrains.annotations.NotNull;
import org.mzi.core.tele.Tele;
import org.mzi.core.tele.Telescopic;

/**
 * @author ice1000
 */
public record PiTerm(@NotNull Tele telescope) implements Term, Telescopic {
  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitPi(this, p);
  }
}
