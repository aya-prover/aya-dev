// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.terck;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableLinkedHashMap;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Call graph is a multi-graph; each vertex represents a definition and each edge from vertex `f`
 * to vertex `g` represents a call to `g` within `f`. The edges are labeled with call matrices,
 * and can be labelled with several call matrices if there are several paths from `f` to `g`.
 *
 * @author kiva
 * @see CallMatrix
 */
public record CallGraph<T, P>(
  @NotNull MutableMap<T, @NotNull MutableMap<T, MutableList<@NotNull CallMatrix<T, P>>>> graph
) {
  public static <T, P> @NotNull CallGraph<T, P> create() {
    return new CallGraph<>(MutableLinkedHashMap.of());
  }

  public void put(@NotNull CallMatrix<T, P> matrix) {
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
  private static <T, P> @NotNull CallGraph<T, P> complete(@NotNull CallGraph<T, P> initial) {
    var step = initial;
    while (true) {
      var comb = indirect(initial, step);
      var tup = merge(comb, step);
      if (tup._1.isEmpty()) return step; // no better matrices are found, we are complete
      step = tup._2; // got a partially completed call graph, continue next cycle
    }
  }

  /** find all indirect calls and combine them together */
  private static <T, P> @NotNull CallGraph<T, P> indirect(@NotNull CallGraph<T, P> initial, @NotNull CallGraph<T, P> step) {
    var comb = CallGraph.<T, P>create();
    initial.graph.forEach((domain, codomains) -> codomains.forEach((codomain, mats) -> mats.forEach(mat -> {
      var indirect = step.graph.getOrNull(mat.codomain());
      if (indirect != null) indirect.forEach((indCodomain, indMats) -> indMats.forEach(ind -> {
        var combine = CallMatrix.combine(mat, ind);
        comb.put(combine);
      }));
    })));
    return comb;
  }

  /** merge newly discovered indirect matrices with old ones, dropping less-decreased. */
  private static <T, P> @NotNull Tuple2<CallGraph<T, P>, CallGraph<T, P>> merge(
    @NotNull CallGraph<T, P> comb, @NotNull CallGraph<T, P> cs
  ) {
    var new_ = mapGraph(comb.graph, a -> Tuple.of(a, MutableList.<CallMatrix<T, P>>create()));
    var old_ = mapGraph(cs.graph, a -> Tuple.of(MutableList.<CallMatrix<T, P>>create(), a));
    var u = unionGraphWith(new_, old_, (n, o) -> {
      var new1 = n._1;
      var new2 = o._1;
      var old2 = o._2;
      var sub = Selector.select(new1.view(), old2.view());
      return Tuple.of(MutableList.from(sub._1.appendedAll(new2)), MutableList.from(sub._1.appendedAll(sub._2)));
    });
    var o = unzipGraph(u);
    return Tuple.of(new CallGraph<>(o._1), new CallGraph<>(o._2));
  }

  public static <K, V, V2> @NotNull MutableMap<K, MutableMap<K, V2>> mapGraph(
    @NotNull MutableMap<K, MutableMap<K, V>> graph,
    @NotNull Function<V, V2> mapper
  ) {
    var newGraph = MutableMap.<K, MutableMap<K, V2>>create();
    graph.forEach((k, ts) -> ts.forEach((x, t) -> {
      var n = mapper.apply(t);
      newGraph.getOrPut(k, MutableLinkedHashMap::of).put(x, n);
    }));
    return newGraph;
  }

  public static <K, V> @NotNull Tuple2<MutableMap<K, MutableMap<K, V>>, MutableMap<K, MutableMap<K, V>>>
  unzipGraph(@NotNull MutableMap<K, MutableMap<K, Tuple2<V, V>>> zipped) {
    var left = MutableLinkedHashMap.<K, MutableMap<K, V>>of();
    var right = MutableLinkedHashMap.<K, MutableMap<K, V>>of();
    zipped.forEach((k, ts) -> ts.forEach((x, t) -> {
      left.getOrPut(k, MutableLinkedHashMap::of).put(x, t._1);
      right.getOrPut(k, MutableLinkedHashMap::of).put(x, t._2);
    }));
    return Tuple.of(left, right);
  }

  public static <K, V> @NotNull MutableMap<K, MutableMap<K, V>> unionGraphWith(
    @NotNull MutableMap<K, MutableMap<K, V>> a,
    @NotNull MutableMap<K, MutableMap<K, V>> b,
    @NotNull BiFunction<V, V, V> combine
  ) {
    return unionMapWith(a, b, (v1, v2) -> unionMapWith(v1, v2, combine));
  }

  public static <K, V> @NotNull MutableMap<K, V> unionMapWith(
    @NotNull MutableMap<K, V> a,
    @NotNull MutableMap<K, V> b,
    @NotNull BiFunction<V, V, V> combine
  ) {
    return MutableLinkedHashMap.from(a.view().map((k, av) -> {
      var bv = b.getOrNull(k);
      return Tuple.of(k, bv == null ? av : combine.apply(av, bv));
    }));
  }

  public @Nullable ImmutableSeq<Diagonal<T, P>> findBadRecursion() {
    var complete = complete(this);
    for (var key : complete.graph.keysView()) {
      var matrix = complete.graph.getOption(key)
        .flatMap(g -> g.getOption(key));
      if (matrix.isEmpty()) continue;
      var ds = matrix.get().view().map(Diagonal::create).toImmutableSeq();
      var bad = ds.filterNot(diag -> diag.diagonal().anyMatch(Relation::isDecreasing));
      if (bad.isNotEmpty()) return bad;
    }
    return null;
  }
}
