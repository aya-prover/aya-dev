// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.error;

import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;

@FunctionalInterface
public interface PosedConsumer<T> extends BiConsumer<SourcePos, T> {
  default void accept(@NotNull WithPos<T> a) {
    accept(a.sourcePos(), a.data());
  }

  default void forceAccept(T a) {
    accept(SourcePos.NONE, a);
  }
}
