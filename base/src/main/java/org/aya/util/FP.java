// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.util;

import org.glavo.kala.control.Either;
import org.glavo.kala.tuple.Tuple2;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public interface FP {
  @Contract(value = "_ -> new", pure = true) static <A, B, C> @NotNull Tuple2<@NotNull Either<A, B>, C>
  transposeSnd(@NotNull Either<@NotNull Tuple2<A, C>, @NotNull Tuple2<B, C>> either) {
    return Tuple2.of(
      either.map(a -> a._1, b -> b._1),
      either.fold(a -> a._2, b -> b._2));
  }

  @Contract(value = "_ -> new", pure = true) static <A, B, C> @NotNull Tuple2<C, @NotNull Either<A, B>>
  transposeFst(@NotNull Either<@NotNull Tuple2<C, A>, @NotNull Tuple2<C, B>> either) {
    return Tuple2.of(
      either.fold(a -> a._1, b -> b._1),
      either.map(a -> a._2, b -> b._2));
  }
}
