package org.mzi.concrete.term;

import org.jetbrains.annotations.NotNull;
import org.mzi.api.error.SourcePos;

/**
 * @author re-xyr
 */
// TODO: sort system - corresponding to the core syntax
public record UnivExpr(
  @NotNull SourcePos sourcePos
) implements Expr {
  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitUniv(this, p);
  }
}
