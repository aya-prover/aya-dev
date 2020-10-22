package org.mzi.concrete.term;

import org.jetbrains.annotations.NotNull;
import org.mzi.api.error.SourcePos;
import org.mzi.api.ref.Ref;

/**
 * @author re-xyr
 */
public record Param(
  @NotNull SourcePos sourcePos,
  @NotNull Ref ref,
  @NotNull Expr type,
  boolean explicit
) {
}
