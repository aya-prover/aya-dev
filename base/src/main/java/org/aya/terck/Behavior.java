// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.terck;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableSet;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Docile;
import org.jetbrains.annotations.NotNull;

import java.util.stream.IntStream;

public record Behavior<T, P>(
  @NotNull T of,
  @NotNull ImmutableSeq<Diag<T, P>> diagonals
) {
  public static <T, P> @NotNull Behavior<T, P> create(@NotNull T of, @NotNull MutableSet<CallMatrix<T, P>> matrix) {
    var diagonals = matrix.view().map(Diag::create).toImmutableSeq();
    return new Behavior<>(of, diagonals);
  }

  public record Diag<T, P>(
    @NotNull CallMatrix<T, P> matrix,
    @NotNull ImmutableSeq<Relation> diagonal
  ) implements Docile {
    public static <T, P> @NotNull Diag<T, P> create(@NotNull CallMatrix<T, P> matrix) {
      assert matrix.rows() == matrix.cols();
      var diag = IntStream.range(0, matrix.rows())
        .mapToObj(i -> matrix.matrix()[i][i])
        .collect(ImmutableSeq.factory());
      return new Diag<>(matrix, diag);
    }

    public @NotNull Doc toDoc() {
      return Doc.stickySep(diagonal.view().map(i -> Doc.plain(i.toString())));
    }
  }
}
