package org.mzi.api.core.ref;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.ref.Ref;

/**
 * @author kiva
 */
@ApiStatus.NonExtendable
public interface Bind<T> {
  @Contract(pure = true) @NotNull Ref ref();
  @Contract(pure = true) @Nullable Bind<T> next();
  @Contract(pure = true) @Nullable T type();
  @Contract(pure = true) boolean explicit();
}
