// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.terck;

import kala.collection.immutable.ImmutableSeq;
import org.aya.core.term.Term;
import org.aya.util.ArrayUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public record CallMatrix<T>(
  int rows, int cols,
  @NotNull T caller, @NotNull T callee,
  @NotNull ImmutableSeq<Term.Param> callerTele,
  @NotNull ImmutableSeq<Term.Param> calleeTele,
  @NotNull Relation[][] matrix
) {
  public static <T> @NotNull CallMatrix<T> create(
    @NotNull Function<T, ImmutableSeq<Term.Param>> arityCounter,
    @NotNull T caller, @NotNull T callee
  ) {
    var callerTele = arityCounter.apply(caller);
    var calleeTele = arityCounter.apply(callee);
    return new CallMatrix<>(calleeTele.size(), callerTele.size(), caller, callee,
      callerTele, calleeTele);
  }

  private CallMatrix(int rows, int cols, @NotNull T caller, @NotNull T callee,
                     @NotNull ImmutableSeq<Term.Param> callerTele,
                     @NotNull ImmutableSeq<Term.Param> calleeTele) {
    // TODO: sparse matrix?
    this(rows, cols, caller, callee, callerTele, calleeTele, new Relation[rows][cols]);
    assert calleeTele.sizeEquals(rows);
    assert callerTele.sizeEquals(cols);
    ArrayUtil.fill(matrix, Relation.Unknown);
  }

  public void set(@NotNull Term.Param caller, @NotNull Term.Param callee, @NotNull Relation relation) {
    int row = calleeTele.indexOf(callee);
    int col = callerTele.indexOf(caller);
    assert row != -1;
    assert col != -1;
    matrix[row][col] = relation;
  }

  @Contract(pure = true)
  public @NotNull CallMatrix<T> mul(@NotNull CallMatrix<T> rhs) {
    if (this.cols != rhs.rows) throw new IllegalArgumentException("matrix multiplication?");
    var result = new CallMatrix<>(this.rows, rhs.cols, caller, rhs.callee, callerTele, rhs.calleeTele);
    for (int i = 0; i < result.rows; i++)
      for (int j = 0; j < result.cols; j++)
        for (int k = 0; k < this.cols; k++)
          result.matrix[i][j] = result.matrix[i][j].add(this.matrix[i][k].mul(rhs.matrix[k][j]));
    return result;
  }
}
