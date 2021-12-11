// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.terck;

import kala.collection.immutable.ImmutableSeq;
import org.aya.util.ArrayUtil;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public record CallMatrix<Def, Param>(
  @NotNull SourcePos sourcePos,
  @NotNull Def domain, @NotNull Def codomain,
  @NotNull ImmutableSeq<Param> domainTele,
  @NotNull ImmutableSeq<Param> codomainTele,
  @NotNull Relation[][] matrix
) {
  public CallMatrix(
    @NotNull SourcePos sourcePos,
    @NotNull Def domain, @NotNull Def codomain,
    @NotNull ImmutableSeq<Param> domainTele,
    @NotNull ImmutableSeq<Param> codomainTele
  ) {
    // TODO: sparse matrix?
    this(sourcePos, domain, codomain, domainTele, codomainTele,
      new Relation[codomainTele.size()][domainTele.size()]);
    ArrayUtil.fill(matrix, Relation.Unknown);
  }

  public int rows() {
    return codomainTele.size();
  }

  public int cols() {
    return domainTele.size();
  }

  public void set(@NotNull Param domain, @NotNull Param codomain, @NotNull Relation relation) {
    int row = codomainTele.indexOf(codomain);
    int col = domainTele.indexOf(domain);
    assert row != -1;
    assert col != -1;
    matrix[row][col] = relation;
  }

  public @NotNull Relation compare(@NotNull CallMatrix<Def, Param> other) {
    if (this.domain != other.domain || this.codomain != other.codomain)
      throw new IllegalArgumentException("Cannot compare unrelated call matrices");
    if (this == other) return Relation.Equal;
    for (int i = 0; i < rows(); i++)
      for (int j = 0; j < cols(); j++) {
        if (!this.matrix[i][j].lessThanOrEqual(other.matrix[i][j]))
          return Relation.Unknown;
      }
    return Relation.LessThan;
  }

  @Contract(pure = true)
  public static <Def, Param> @NotNull CallMatrix<Def, Param> combine(
    @NotNull CallMatrix<Def, Param> A, @NotNull CallMatrix<Def, Param> B
  ) {
    if (B.domain != A.codomain) // implies B.cols() != A.rows()
      throw new IllegalArgumentException("The combine cannot be applied to these two call matrices");

    var BA = new CallMatrix<>(B.sourcePos, A.domain, B.codomain,
      A.domainTele, B.codomainTele);

    for (int i = 0; i < BA.rows(); i++)
      for (int j = 0; j < BA.cols(); j++)
        for (int k = 0; k < B.cols(); k++)
          BA.matrix[i][j] = BA.matrix[i][j].add(B.matrix[i][k].mul(A.matrix[k][j]));
    return BA;
  }
}
