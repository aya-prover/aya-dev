package org.mzi.api.util;

import org.jetbrains.annotations.NotNull;
import org.mzi.api.error.SourcePos;
import org.mzi.api.ref.Ref;

/**
 * @author kiva
 */
public record PositionedRef(
  @NotNull Ref ref,
  @NotNull SourcePos pos
) {
}
