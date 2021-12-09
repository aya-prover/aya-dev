// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.terck;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableSet;
import org.jetbrains.annotations.NotNull;

import java.util.stream.IntStream;

public record Behavior<T>(
  @NotNull T of,
  @NotNull ImmutableSeq<Diag<T>> diagonals
) {
  public static <T> @NotNull Behavior<T> create(@NotNull T of, @NotNull MutableSet<CallMatrix<T>> matrix) {
    var diagonals = matrix.view().map(Diag::create).toImmutableSeq();
    return new Behavior<>(of, diagonals);
  }

  public record Diag<T>(
    @NotNull CallMatrix<T> matrix,
    @NotNull ImmutableSeq<Relation> diagonal
  ) {
    public static <T> @NotNull Diag<T> create(@NotNull CallMatrix<T> matrix) {
      assert matrix.rows() == matrix.cols();
      var diag = IntStream.range(0, matrix.rows())
        .mapToObj(i -> matrix.matrix()[i][i])
        .collect(ImmutableSeq.factory());
      return new Diag<>(matrix, diag);
    }
  }
}
