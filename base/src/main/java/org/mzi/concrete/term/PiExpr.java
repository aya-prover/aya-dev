package org.mzi.concrete.term;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author re-xyr
 */
public record PiExpr(@NotNull List<@NotNull String> binds, @NotNull Expr body) implements Expr {
  @Override
  public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitPi(this, p);
  }
}
