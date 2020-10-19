package org.mzi.concrete.term;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author re-xyr
 */
public record PiTerm(@NotNull List<@NotNull String> binds, @NotNull Term body) implements Term {
  @Override
  public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitPi(this, p);
  }
}
