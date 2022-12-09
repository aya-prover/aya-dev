// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.terck;

import kala.collection.immutable.ImmutableSeq;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Docile;
import org.aya.util.terck.Relation;
import org.jetbrains.annotations.NotNull;

import java.util.stream.IntStream;

public record Diagonal<T, P>(
  @NotNull CallMatrix<T, P> matrix,
  @NotNull ImmutableSeq<Relation> diagonal
) implements Docile {
  public static <T, P> @NotNull Diagonal<T, P> create(@NotNull CallMatrix<T, P> matrix) {
    assert matrix.rows() == matrix.cols();
    var diag = IntStream.range(0, matrix.rows())
      .mapToObj(i -> matrix.matrix()[i][i])
      .collect(ImmutableSeq.factory());
    return new Diagonal<>(matrix, diag);
  }

  public @NotNull Doc toDoc() {
    return Doc.stickySep(diagonal.view().map(Relation::toDoc));
  }
}
