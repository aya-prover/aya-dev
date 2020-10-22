package org.mzi.concrete.term;

import org.jetbrains.annotations.NotNull;
import org.mzi.api.util.Ident;

/**
 * @author re-xyr
 */
public record UnresolvedExpr(@NotNull Ident name) implements Expr {
  @Override
  public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitUnresolved(this, p);
  }
}
