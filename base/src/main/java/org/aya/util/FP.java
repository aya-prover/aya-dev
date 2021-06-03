// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.util;

import kala.control.Either;
import kala.tuple.Tuple2;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public interface FP {
  @Contract(value = "_ -> new", pure = true) static <A, B, C> @NotNull Tuple2<@NotNull Either<A, B>, C>
  distL(@NotNull Either<@NotNull Tuple2<A, C>, @NotNull Tuple2<B, C>> either) {
    return Tuple2.of(
      either.map(Tuple2::getKey, Tuple2::getKey),
      either.fold(Tuple2::getValue, Tuple2::getValue));
  }

  @Contract(value = "_ -> new", pure = true) static <A, B, C> @NotNull Tuple2<C, @NotNull Either<A, B>>
  distR(@NotNull Either<@NotNull Tuple2<C, A>, @NotNull Tuple2<C, B>> either) {
    return Tuple2.of(
      either.fold(Tuple2::getKey, Tuple2::getKey),
      either.map(Tuple2::getValue, Tuple2::getValue));
  }
}
