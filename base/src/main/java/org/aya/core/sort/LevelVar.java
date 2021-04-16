// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.sort;

import org.aya.api.ref.Var;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @param bound true if this is a bound level var, otherwise it needs to be solved.
 *              In well-typed terms it should always be true.
 * @author ice1000
 */
public record LevelVar(
  @NotNull String name,
  boolean bound
) implements Var {
  @Override public boolean equals(@Nullable Object o) {
    return this == o;
  }

  @Override public int hashCode() {
    return System.identityHashCode(this);
  }
}
