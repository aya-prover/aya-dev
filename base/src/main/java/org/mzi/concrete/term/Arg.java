package org.mzi.concrete.term;

import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public record Arg(
  @NotNull Expr expr,
  boolean isExplicit
) {
}
