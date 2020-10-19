package org.mzi.core.ref;

import org.jetbrains.annotations.NotNull;
import org.mzi.api.core.ref.CoreBind;
import org.mzi.ref.Ref;

/**
 * @author ice1000
 */
public record Bind(
  @NotNull Ref ref,
  boolean isExplicit
) implements CoreBind {
}
