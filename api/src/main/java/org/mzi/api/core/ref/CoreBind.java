package org.mzi.api.core.ref;

import org.jetbrains.annotations.NotNull;

/**
 * @author kiva
 */
public interface CoreBind {
  @NotNull CoreRef ref();

  boolean isExplicit();
}
