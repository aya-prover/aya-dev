// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.terck;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.range.primitive.IntRange;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Docile;
import org.jetbrains.annotations.NotNull;

public record Diagonal<C, T, P>(
  @NotNull CallMatrix<C, T, P> matrix,
  @NotNull ImmutableSeq<Relation> diagonal
) implements Docile {
  public static <C, T, P> @NotNull Diagonal<C, T, P> create(@NotNull CallMatrix<C, T, P> matrix) {
    assert matrix.rows() == matrix.cols();
    var diag = IntRange.closedOpen(0, matrix.rows())
      .mapToObjTo(MutableList.create(), i -> matrix.matrix()[i][i])
      .toImmutableSeq();
    return new Diagonal<>(matrix, diag);
  }

  public @NotNull Doc toDoc() {
    return Doc.stickySep(diagonal.view().map(Relation::toDoc));
  }
}
