// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.generic;

import kala.collection.mutable.Buffer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * @param <T> the tree
 * @author ice1000
 */
public abstract class GenericBuilder<T extends GenericBuilder.Tree<T>> {
  public interface Tree<T extends Tree<T>> {
    @NotNull Buffer<T> children();
  }

  protected final Deque<@NotNull Buffer<@NotNull T>> tops = new ArrayDeque<>();

  public @NotNull Buffer<@NotNull T> root() {
    return tops.getFirst();
  }

  {
    tops.addLast(Buffer.create());
  }

  public void append(@NotNull T trace) {
    shift(trace);
    reduce();
  }

  public void shift(@NotNull T trace) {
    Objects.requireNonNull(tops.getLast()).append(trace);
    tops.addLast(trace.children());
  }

  public void unshift() {
    var buffer = Objects.requireNonNull(tops.getLast());
    buffer.removeAt(buffer.size() - 1);
  }

  public void reduce() {
    tops.removeLast();
  }
}
