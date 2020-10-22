package org.mzi.concrete.term;

import asia.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.error.SourcePos;

/**
 * @author re-xyr
 */
public record LamExpr(
  @NotNull SourcePos sourcePos,
  @NotNull ImmutableSeq<@NotNull Param> binds,
  @NotNull Expr body
) implements Expr {
  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitLam(this, p);
  }
}
