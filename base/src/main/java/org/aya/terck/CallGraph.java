// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.terck;

import kala.collection.mutable.MutableMap;
import kala.collection.mutable.MutableSet;
import org.jetbrains.annotations.NotNull;

public record CallGraph<T>(
  @NotNull MutableMap<T, @NotNull MutableMap<T, MutableSet<@NotNull CallMatrix<T>>>> graph
) {
  public static <T> @NotNull CallGraph<T> create() {
    return new CallGraph<>(MutableMap.create());
  }

  /**
   * Add a call matrix to the graph, if there's no same call matrix from
   * {@link CallMatrix#caller()} to {@link CallMatrix#callee()} was found.
   *
   * @return whether the matrix it is added to the graph
   */
  public boolean put(@NotNull CallMatrix<T> matrix) {
    var caller = matrix.caller();
    var callee = matrix.callee();
    var set = graph.getOrPut(caller, MutableMap::create)
      .getOrPut(callee, MutableSet::create);
    if (set.contains(matrix)) return false;
    set.add(matrix);
    return true;
  }
}
