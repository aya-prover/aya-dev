package org.mzi.concrete.term;

import org.jetbrains.annotations.NotNull;
import org.mzi.api.ref.Ref;

/**
 * @author ice1000
 */
public record RefExpr(@NotNull Ref resolvedRef) implements Expr {
  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitRef(this, p);
  }
}
