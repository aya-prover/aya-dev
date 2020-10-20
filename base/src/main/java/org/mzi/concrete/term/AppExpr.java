package org.mzi.concrete.term;

import asia.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;

/**
 * @author re-xyr
 */
public record AppExpr(
  @NotNull Expr function,
  @NotNull ImmutableSeq<@NotNull Arg> argument
) implements Expr {
  @Override
  public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitApp(this, p);
  }

  public static record Arg(
    @NotNull Expr expr,
    boolean explicit
  ) {
  }
}
