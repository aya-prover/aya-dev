package org.mzi.core.term;

import org.jetbrains.annotations.NotNull;
import org.mzi.core.ref.Tele;

import java.util.List;

/**
 * @author ice1000
 */
public record LamTerm(@NotNull List<@NotNull Tele> teles, @NotNull Term body) implements Term {
  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitLam(this, p);
  }
}
