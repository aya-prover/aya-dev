// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.terck;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableLinkedHashMap;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Call graph is a multi-graph; each vertex represents a definition and each edge from vertex `f`
 * to vertex `g` represents a call to `g` within `f`. The edges are labeled with call matrices,
 * and can be labelled with several call matrices if there are several paths from `f` to `g`.
 *
 * @author kiva
 * @see CallMatrix
 */
public record CallGraph<C, T, P>(
  @NotNull MutableMap<T, @NotNull MutableMap<T, MutableList<@NotNull CallMatrix<C, T, P>>>> graph
) {
  public static <C, T, P> @NotNull CallGraph<C, T, P> create() {
    return new CallGraph<>(MutableLinkedHashMap.of());
  }

  public void put(@NotNull CallMatrix<C, T, P> matrix) {
    var caller = matrix.domain();
    var callee = matrix.codomain();
    var calls = graph.getOrPut(caller, MutableLinkedHashMap::of)
      .getOrPut(callee, MutableList::create);
    calls.append(matrix);
  }

  /** @return true if there's no edge */
  public boolean isEmpty() {
    return graph.allMatch((k, ts) -> ts.allMatch((x, t) -> t.isEmpty()));
  }

  /** completing a call graph is just finding its transitive closure */
  private static <C, T, P> @NotNull CallGraph<C, T, P> complete(@NotNull CallGraph<C, T, P> initial) {
    var step = initial;
    while (true) {
      var comb = indirect(initial, step);
      var tup = merge(comb, step);
      if (tup.component1().isEmpty()) return step; // no better matrices are found, we are complete
      step = tup.component2(); // got a partially completed call graph, try complete more
    }
  }

  /** find all indirect calls and combine them together */
  private static <C, T, P> @NotNull CallGraph<C, T, P> indirect(@NotNull CallGraph<C, T, P> initial, @NotNull CallGraph<C, T, P> step) {
    var comb = CallGraph.<C, T, P>create();
    initial.graph.forEach((domain, codomains) -> codomains.forEach((codomain, mats) -> mats.forEach(mat -> {
      var indirect = step.graph.getOrNull(mat.codomain());
      if (indirect != null) indirect.forEach((indCodomain, indMats) -> indMats.forEach(ind -> {
        var combine = CallMatrix.combine(mat, ind);
        comb.put(combine);
      }));
    })));
    return comb;
  }

  /**
   * merge newly discovered indirect matrices with old ones.
   * <a href="https://github.com/agda/agda/blob/e3bf58d8b2e95bc0481035756f44ddd9fe19b40d/src/full/Agda/Termination/CallGraph.hs#L155">CallGraph.hs</a>
   */
  private static <C, T, P> @NotNull Tuple2<CallGraph<C, T, P>, CallGraph<C, T, P>> merge(
    @NotNull CallGraph<C, T, P> comb, @NotNull CallGraph<C, T, P> cs
  ) {
    var newG = CallGraph.<C, T, P>create(); // all accepted new matrices go here, used for indicating whether we are done.
    var oldG = CallGraph.<C, T, P>create(); // all old matrices and accepted new matrices go here
    forEachGraph(comb.graph, cs.graph,
      // If the matrix is really new (no old matrices describing the same call -- we find a new call path), accept it
      n -> {
        n.forEach(newG::put);
        n.forEach(oldG::put);
      },
      // If no new matrix is replacing the old one, keep the old one
      o -> o.forEach(oldG::put),
      // If `n` is replacing `o`, compare one by one
      (n, o) -> {
        // check if there's still old ones better than new ones...
        // note: the idea of "better" is not the same as "decrease more", see comments on `Selector.select()`
        var cmp = Selector.select(n.view(), o.view());
        cmp.component1().forEach(newG::put); // filtered really better new ones,
        cmp.component1().forEach(oldG::put); // ... and accept them.
        cmp.component2().forEach(oldG::put); // filtered old ones that still better than new ones.
      });
    return Tuple.of(newG, oldG);
  }

  public static <K, V> void forEachGraph(
    @NotNull MutableMap<K, MutableMap<K, V>> a,
    @NotNull MutableMap<K, MutableMap<K, V>> b,
    @NotNull Consumer<V> inA,
    @NotNull Consumer<V> inB,
    @NotNull BiConsumer<V, V> both
  ) {
    forEachMap(a, b,
      v1 -> v1.forEach((k, v) -> inA.accept(v)),
      v2 -> v2.forEach((k, v) -> inB.accept(v)),
      (v1, v2) -> forEachMap(v1, v2, inA, inB, both));
  }

  public static <K, V> void forEachMap(
    @NotNull MutableMap<K, V> a,
    @NotNull MutableMap<K, V> b,
    @NotNull Consumer<V> inA,
    @NotNull Consumer<V> inB,
    @NotNull BiConsumer<V, V> both
  ) {
    var union = MutableLinkedHashMap.<K, V>of();
    a.forEach(union::put);
    b.forEach((k, bv) -> {
      var av = union.remove(k);
      if (av.isEmpty()) inB.accept(bv);
      else both.accept(av.get(), bv);
    });
    union.forEach((k, av) -> inA.accept(av));
  }

  /** find bad recursive calls in current SCC */
  public @NotNull ImmutableSeq<Diagonal<C, T, P>> findBadRecursion() {
    var complete = complete(this);
    var bads = MutableList.<Diagonal<C, T, P>>create();
    for (var key : complete.graph.keysView()) {
      var matrix = complete.graph.getOption(key)
        .flatMap(g -> g.getOption(key));
      if (matrix.isEmpty()) continue;
      // idempotent calls can never get worse after completion --- they are already at the bottom.
      var idempotent = matrix.get().view()
        .filter(m -> CallMatrix.combine(m, m).notWorseThan(m));
      // size-change principle: each idempotent call matrix must have a decreasing argument.
      var bad = idempotent
        .map(Diagonal::create)
        .filterNot(diag -> diag.diagonal().anyMatch(Relation::isDecreasing))
        .toImmutableSeq();
      if (bad.isNotEmpty()) bads.appendAll(bad);
    }
    return bads.toImmutableSeq();
  }
}
