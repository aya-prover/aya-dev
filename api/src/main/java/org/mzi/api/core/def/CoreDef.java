package org.mzi.api.core.def;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.ref.Var;

/**
 * @author kiva
 */
@ApiStatus.NonExtendable
public interface CoreDef {
  @Contract(pure = true) @NotNull Var ref();
}
