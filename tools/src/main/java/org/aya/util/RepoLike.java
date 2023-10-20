// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface RepoLike<T> {
  void setDownstream(@Nullable T downstream);

  default void merge() {
    setDownstream(null);
  }

  default void fork(@NotNull T t) {
    setDownstream(t);
  }
}
