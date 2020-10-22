package org.mzi.concrete.term;

import asia.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.error.SourcePos;
import org.mzi.generic.DTKind;

/**
 * @author re-xyr
 */
public record DTExpr(
  @NotNull SourcePos sourcePos,
  @NotNull ImmutableSeq<@NotNull Param> binds,
  @NotNull DTKind kind
) implements Expr {
  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitDT(this, p);
  }
}
