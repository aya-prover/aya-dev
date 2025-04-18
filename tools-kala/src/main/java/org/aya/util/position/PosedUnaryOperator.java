// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.position;

import org.jetbrains.annotations.NotNull;

import java.util.function.BiFunction;

@FunctionalInterface
public interface PosedUnaryOperator<T> extends BiFunction<SourcePos, T, T> {
  static <T> PosedUnaryOperator<T> identity() { return (_, t) -> t; }
  default T apply(@NotNull WithPos<T> a) { return apply(a.sourcePos(), a.data()); }
  default T forceApply(T a) { return apply(SourcePos.NONE, a); }
}
