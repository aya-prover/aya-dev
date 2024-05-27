// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.error;

import org.jetbrains.annotations.NotNull;

import java.util.function.BiFunction;

@FunctionalInterface
public interface PosedUnaryOperator<T> extends BiFunction<SourcePos, T, T> {
  default T apply(@NotNull WithPos<T> a) {
    return apply(a.sourcePos(), a.data());
  }

  default T forceApply(T a) {
    return apply(SourcePos.NONE, a);
  }
}
