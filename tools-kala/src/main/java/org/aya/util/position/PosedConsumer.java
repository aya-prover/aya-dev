// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.position;

import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;

@FunctionalInterface
public interface PosedConsumer<T> extends BiConsumer<SourcePos, T> {
  default void accept(@NotNull WithPos<T> a) { accept(a.sourcePos(), a.data()); }
}
