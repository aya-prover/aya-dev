// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.utils;

import kala.value.MutableValue;
import org.jetbrains.annotations.NotNull;

public interface RepoLike<T> {
  @NotNull MutableValue<T> downstream();

  default void merge() {
    downstream().set(null);
  }

  default void fork(@NotNull T t) {
    downstream().set(t);
  }
}
