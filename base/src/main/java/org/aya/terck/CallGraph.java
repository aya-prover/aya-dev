// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.terck;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.DynamicSeq;
import kala.collection.mutable.MutableMap;
import kala.collection.mutable.MutableSet;
import kala.control.Option;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.NotNull;

public record CallGraph<T, P>(
  @NotNull MutableMap<T, @NotNull MutableMap<T, MutableSet<@NotNull CallMatrix<T, P>>>> graph
) {
  public static <T, P> @NotNull CallGraph<T, P> create() {
    return new CallGraph<>(MutableMap.create());
  }

  /**
   * Add a call matrix to the graph, if there's no same call matrix from
   * {@link CallMatrix#domain()} to {@link CallMatrix#codomain()} was found.
   *
   * @return whether the matrix it is added to the graph
   */
  public boolean put(@NotNull CallMatrix<T, P> matrix) {
    var caller = matrix.domain();
    var callee = matrix.codomain();
    var set = graph.getOrPut(caller, MutableMap::create)
      .getOrPut(callee, MutableSet::create);
    // TODO: check if there's already a smaller call matrix?
    return set.add(matrix);
  }

  public @NotNull ImmutableSeq<WithPos<T>> findNonTerminating() {
    // TODO: complete the call graph first
    var failed = DynamicSeq.<WithPos<T>>create();
    for (var key : graph.keysView()) {
      var matrix = Option.of(graph.getOrNull(key))
        .mapNotNull(g -> g.getOrNull(key));
      if (matrix.isEmpty()) continue;
      var behavior = Behavior.create(key, matrix.get());
      // Each diagonal in the behavior is a possible recursive call to `key` and how the orders of all parameters are
      // altered in this call. We ensure in each possible recursive call, there's at least one parameter decreases.
      // note[kiva]: Arend uses foetus termination check. Agda used to choose foetus, but now it applies
      // size-change termination check (every idempotent call must have decrease), see links below:
      // https://github.com/agda/agda/blob/master/src/full/Agda/Termination/Termination.hs
      var notDecreasing = behavior.diagonals()
        .filterNot(diag -> diag.diagonal().contains(Relation.LessThan));
      notDecreasing.map(diag -> new WithPos<>(diag.matrix().sourcePos(), key))
        .forEach(failed::append);
    }
    return failed.toImmutableSeq();
  }
}
