// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.terck;

import org.aya.util.ArrayUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public record CallMatrix<T>(
  int rows,
  int cols,
  @NotNull T caller,
  @NotNull T callee,
  @NotNull Relation[][] matrix
) {
  public CallMatrix(@NotNull Function<T, Integer> arityCounter, @NotNull T caller, @NotNull T callee) {
    this(arityCounter.apply(callee), arityCounter.apply(caller), caller, callee);
  }

  public CallMatrix(int rows, int cols, @NotNull T caller, @NotNull T callee) {
    // TODO: sparse matrix?
    this(rows, cols, caller, callee, new Relation[rows][cols]);
    ArrayUtil.fill(matrix, Relation.Unknown);
  }

  @Contract(pure = true)
  public @NotNull CallMatrix<T> mul(@NotNull CallMatrix<T> rhs) {
    if (this.cols != rhs.rows) throw new IllegalArgumentException("matrix multiplication?");
    var result = new CallMatrix<>(this.rows, rhs.cols, caller, rhs.callee);
    for (int i = 0; i < result.rows; i++)
      for (int j = 0; j < result.cols; j++)
        for (int k = 0; k < this.cols; k++)
          result.matrix[i][j] = result.matrix[i][j].add(this.matrix[i][k].mul(rhs.matrix[k][j]));
    return result;
  }
}
