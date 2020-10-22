package org.mzi.concrete.term;

import org.jetbrains.annotations.NotNull;
import org.mzi.api.util.PositionedRef;

/**
 * @author re-xyr
 */
public record Param(
  @NotNull PositionedRef ref,
  @NotNull Expr type,
  boolean explicit
) {
}
