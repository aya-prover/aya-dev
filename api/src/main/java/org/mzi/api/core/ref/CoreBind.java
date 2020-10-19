package org.mzi.api.core.ref;

import org.jetbrains.annotations.NotNull;
import org.mzi.api.ref.Ref;

/**
 * @author kiva
 */
public interface CoreBind {
  @NotNull Ref ref();

  boolean isExplicit();
}
