// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.pat;

import org.glavo.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * @author ice1000, kiva
 */
public record PatTree(@NotNull String s, @NotNull Buffer<PatTree> trees) {
  public PatTree(@NotNull String s) {
    this(s, Buffer.create());
  }

  public void buildString(@NotNull StringBuilder builder) {
    if (trees.isEmpty()) {
      builder.append(s);
    } else {
      builder.append("(").append(s);
      for (var tree : trees) tree.buildString(builder.append(", "));
      builder.append(")");
    }
  }

  public static class Builder {
    private final Deque<@NotNull Buffer<@NotNull PatTree>> tops = new ArrayDeque<>();

    public @NotNull Buffer<@NotNull PatTree> root() {
      return tops.getFirst();
    }

    {
      tops.addLast(Buffer.of());
    }

    public void shiftEmpty() {
      shift(new PatTree("_"));
      reduce();
    }

    public void shift(@NotNull PatTree trace) {
      Objects.requireNonNull(tops.getLast()).append(trace);
      tops.addLast(trace.trees());
    }

    public void reduce() {
      tops.removeLast();
    }
  }

}
