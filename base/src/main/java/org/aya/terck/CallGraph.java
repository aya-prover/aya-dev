// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.terck;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.DynamicSeq;
import kala.collection.mutable.MutableMap;
import kala.collection.mutable.MutableSet;
import kala.control.Option;
import kala.value.Ref;
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
    if (set.contains(matrix)) return false;
    var unknown = set.anyMatch(arrow -> arrow.compare(matrix) != Relation.Unknown);
    if (unknown) return false;
    set.removeAll(existing -> matrix.compare(existing) == Relation.LessThan);
    set.add(matrix);
    return true;
  }

  private static <T, P> @NotNull CallGraph<T, P> complete(@NotNull CallGraph<T, P> start) {
    var oldGraph = new Ref<>(start);
    var newGraph = new Ref<>(CallGraph.<T, P>create());
    var newEdge = new Ref<>(1);
    while (newEdge.value != 0) {
      newEdge.value = 0;
      oldGraph.value.graph.forEach((domain, codomains) -> {
        var out = codomains.getOrNull(domain);
        if (out != null) out.forEach(matrix -> newGraph.value.put(matrix));
      });
      oldGraph.value.graph.forEach((domain, codomains) -> codomains.forEach((codomain, mats) -> mats.forEach(matrix -> {
        var indirect = oldGraph.value.graph.getOrNull(matrix.codomain());
        if (indirect != null) indirect.forEach((indCodomain, indMatrices) -> indMatrices.forEach(ind -> {
          var combine = CallMatrix.combine(matrix, ind);
          if (newGraph.value.put(combine)) newEdge.value++;
        }));
      })));
      oldGraph.value = newGraph.value;
      newGraph.value = CallGraph.create();
    }
    return oldGraph.value;
  }

  public @NotNull ImmutableSeq<WithPos<T>> findNonTerminating() {
    var complete = complete(this);
    var failed = DynamicSeq.<WithPos<T>>create();
    for (var key : complete.graph.keysView()) {
      var matrix = Option.of(complete.graph.getOrNull(key))
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
