package org.mzi.api.core.def;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.ref.Ref;

/**
 * @author kiva
 */
@ApiStatus.NonExtendable
public interface CoreDef {
  @NotNull Ref ref();
}
