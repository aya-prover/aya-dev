package org.mzi.api.core.def;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @author kiva
 */
@ApiStatus.NonExtendable
public interface CoreDef {
  @NotNull String name();
}
