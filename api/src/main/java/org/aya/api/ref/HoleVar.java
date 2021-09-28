// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.api.ref;

import org.jetbrains.annotations.NotNull;

/**
 * @param <Core> the type of the thing stored in the hole
 * @author AustinZ, re-xyr
 */
public record HoleVar<Core>(@NotNull String name, @NotNull Core core) implements Var {

  @Override public boolean equals(Object o) {
    return this == o;
  }

  @Override public int hashCode() {
    return System.identityHashCode(this);
  }
}
