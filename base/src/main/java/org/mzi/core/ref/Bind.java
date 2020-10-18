package org.mzi.core.ref;

import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public record Bind(
  @NotNull Ref ref,
  boolean isExplicit
) {
}
