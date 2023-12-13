// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.terck;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.*;
import org.jetbrains.annotations.NotNull;

public record MutableGraph<T>(@NotNull MutableMap<T, @NotNull MutableList<@NotNull T>> E) {
  public static @NotNull <T> MutableGraph<T> create() {
    return new MutableGraph<>(MutableLinkedHashMap.of());
  }

  public @NotNull MutableList<T> sucMut(@NotNull T elem) {
    return E.getOrPut(elem, MutableList::of);
  }

  public @NotNull SeqView<T> suc(@NotNull T elem) {
    var suc = E.getOrNull(elem);
    return suc == null ? SeqView.empty() : suc.view();
  }

  public boolean hasPath(@NotNull T from, @NotNull T to) {
    return hasPath(MutableSet.create(), from, to);
  }

  private boolean hasPath(@NotNull MutableSet<T> book, @NotNull T from, @NotNull T to) {
    if (from == to) return true;
    if (book.contains(from)) return false;
    book.add(from);
    for (var test : suc(from)) if (hasPath(book, test, to)) return true;
    return false;
  }

  public @NotNull ImmutableSeq<ImmutableSeq<T>> findCycles() {
    return topologicalOrder().filter(scc -> scc.sizeGreaterThan(1));
  }

  /**
   * Returns a topological order of the graph
   * whose edge (v, w) means v depends on w.
   */
  public ImmutableSeq<ImmutableSeq<T>> topologicalOrder() {
    return new Tarjan().tarjan();
  }

  public @NotNull MutableGraph<T> transpose() {
    var tr = MutableGraph.<T>create();
    E.forEach((v, ws) -> {
      tr.sucMut(v);
      ws.forEach(w -> tr.sucMut(w).append(v));
    });
    return tr;
  }

  private static class Info {
    static final int INDEX_NONE = Integer.MAX_VALUE;
    int index = INDEX_NONE;
    int lowlink = INDEX_NONE;
    boolean free = false;

    boolean noIndex() {
      return index == INDEX_NONE;
    }
  }

  /**
   * Find strongly connected components in a graph,
   * and return the topological order (need reversing) of the components.
   */
  private class Tarjan {
    final MutableMap<T, Info> info = MutableLinkedHashMap.of();
    final MutableSinglyLinkedList<T> stack = MutableSinglyLinkedList.create();
    final MutableList<ImmutableSeq<T>> SCCs = MutableList.create();
    int index = 0;

    private @NotNull Info info(@NotNull T t) {
      return info.getOrPut(t, Info::new);
    }

    private void push(@NotNull T v) {
      stack.push(v);
      info(v).free = true;
    }

    private @NotNull T pop() {
      var v = stack.pop();
      info(v).free = false;
      return v;
    }

    public void makeSCC(@NotNull T v) {
      var infoV = info(v);
      infoV.index = infoV.lowlink = index++;
      push(v);

      suc(v).forEach(w -> {
        var infoW = info(w);
        if (infoW.noIndex()) {
          makeSCC(w);
          infoV.lowlink = Math.min(infoV.lowlink, infoW.lowlink);
        } else if (infoW.free) {
          // successor `w` is in stack and free, so it is in the current SCC.
          // If `w` is not free, then (v, w) is an edge pointing to an SCC already found,
          // we must ignore `w` or we will get more than one `w` in topological order.
          infoV.lowlink = Math.min(infoV.lowlink, infoW.index);
        }
      });

      // If v is a root node, pop the stack and generate an SCC
      if (infoV.lowlink == infoV.index) {
        var scc = MutableList.<T>create();
        T t = null;
        while (v != t) {
          t = pop();
          scc.append(t);
        }
        SCCs.append(scc.toImmutableSeq());
      }
    }

    public ImmutableSeq<ImmutableSeq<T>> tarjan() {
      // view should be lazy or this code will blow up
      E.keysView().filter(v -> info(v).noIndex()).forEach(this::makeSCC);
      return SCCs.toImmutableSeq();
    }
  }
}
