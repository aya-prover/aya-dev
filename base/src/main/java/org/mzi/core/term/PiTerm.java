package org.mzi.core.term;

import org.jetbrains.annotations.NotNull;
import org.mzi.core.ref.Bind;

import java.util.List;

/**
 * @author ice1000
 */
public record PiTerm(@NotNull List<@NotNull Bind> binds, @NotNull Term body) implements Term {
  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitPi(this, p);
  }
}
