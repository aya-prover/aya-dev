package org.mzi.concrete.term;

import org.jetbrains.annotations.NotNull;
import org.mzi.api.util.PositionedRef;

/**
 * @author ice1000
 */
public record RefExpr(@NotNull PositionedRef resolvedRef) implements Expr {
  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitRef(this, p);
  }
}
