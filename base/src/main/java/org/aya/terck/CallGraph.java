// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.terck;

import kala.collection.mutable.MutableMap;
import kala.collection.mutable.MutableSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    // TODO: check if there's already a smaller call matrix?
    if (set.contains(matrix)) return false;
    set.add(matrix);
    return true;
  }

  public @Nullable T terck() {
    // TODO: complete the call graph first
    for (var key : graph.keysView()) {
      var matrix = graph.get(key).get(key);
      var behavior = Behavior.create(key, matrix);
      // Each diagonal in the behavior is a possible recursive call to `key`
      // and how the orders of all parameters are altered in this call.
      // We ensure in each possible recursive call, there's at least one parameter decreases.
      // TODO[kiva]: ^ is that enough? I see the checking is so complicated in Arend.
      if (behavior.diagonals().allMatch(diag -> diag.diagonal().anyMatch(r -> r == Relation.LessThan))) {
        continue;
      }
      return key;
    }
    return null;
  }
}
