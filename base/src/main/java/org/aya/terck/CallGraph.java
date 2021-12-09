// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.terck;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.DynamicSeq;
import kala.collection.mutable.MutableMap;
import kala.collection.mutable.MutableSet;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import org.aya.util.error.SourcePos;
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
    // TODO: check if there's already a smaller call matrix?
    return set.add(matrix);
  }

  public @NotNull ImmutableSeq<Tuple2<T, SourcePos>> findNonTerminating() {
    // TODO: complete the call graph first
    var failed = DynamicSeq.<Tuple2<T, SourcePos>>create();
    for (var key : graph.keysView()) {
      var matrix = graph.get(key).get(key);
      var behavior = Behavior.create(key, matrix);
      // Each diagonal in the behavior is a possible recursive call to `key`
      // and how the orders of all parameters are altered in this call.
      // We ensure in each possible recursive call, there's at least one parameter decreases.
      // TODO[kiva]: ^ is that enough? I see the checking is so complicated in Arend.
      var notDecreasing = behavior.diagonals()
        .filterNot(diag -> diag.diagonal().contains(Relation.LessThan));
      notDecreasing.forEach(diag -> failed.append(Tuple.of(key, diag.matrix().sourcePos())));
    }
    return failed.toImmutableSeq();
  }
}
