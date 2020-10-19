package org.mzi.api.core.ref;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.ref.Ref;

/**
 * @author kiva
 */
@ApiStatus.NonExtendable
public interface CoreBind {
  @NotNull Ref ref();
  @Nullable CoreBind next();
  boolean explicit();
}
