package org.mzi.concrete.term;

import org.jetbrains.annotations.NotNull;

/**
 * @author re-xyr
 */
// TODO: sort system - corresponding to the core syntax
public record UnivExpr() implements Expr {
  @Override
  public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitUniv(this, p);
  }
}
