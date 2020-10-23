package org.mzi.concrete.term;

import org.jetbrains.annotations.NotNull;
import org.mzi.api.error.SourcePos;
import org.mzi.api.ref.Var;

/**
 * @author re-xyr
 */
public record Param(
  @NotNull SourcePos sourcePos,
  @NotNull Var var,
  @NotNull Expr type,
  boolean explicit
) {
}
