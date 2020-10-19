package org.mzi.concrete.term;

import org.jetbrains.annotations.NotNull;

/**
 * @author re-xyr
 */
public record RefExpr(@NotNull String ref) implements Expr {
  @Override
  public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitRef(this, p);
  }
}
