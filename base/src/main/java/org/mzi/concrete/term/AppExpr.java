package org.mzi.concrete.term;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author ice1000
 */
public record AppExpr(
  @NotNull Expr function,
  @NotNull List<@NotNull Arg> argument
) implements Expr {
  @Override
  public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitApp(this, p);
  }

  public static record Arg(
    @NotNull Expr expr,
    boolean isExplicit
  ) {
  }
}
