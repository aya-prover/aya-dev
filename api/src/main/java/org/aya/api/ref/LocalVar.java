// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.api.ref;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ice1000
 */
public record LocalVar(@NotNull String name) implements Var {
  @Override public boolean equals(@Nullable Object o) {
    return this == o;
  }

  @Override public int hashCode() {
    return System.identityHashCode(this);
  }
}
