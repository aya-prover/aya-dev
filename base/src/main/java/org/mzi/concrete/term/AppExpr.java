package org.mzi.concrete.term;

import asia.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.error.SourcePos;
import org.mzi.generic.Arg;

/**
 * @author re-xyr
 */
public record AppExpr(
  @NotNull SourcePos sourcePos,
  @NotNull Expr function,
  @NotNull ImmutableSeq<@NotNull Arg<Expr>> argument
) implements Expr {
  @Override
  public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitApp(this, p);
  }
}
