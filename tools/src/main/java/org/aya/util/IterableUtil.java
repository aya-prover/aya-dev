// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util;

import java.util.Iterator;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import kala.collection.SeqView;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IterableUtil {
  static <T> void forEach(@NotNull Iterable<T> it, @NotNull BiConsumer<T, T> separator, @NotNull Consumer<T> run) {
    var iter = it.iterator();
    if (!iter.hasNext()) return;
    var last = iter.next();
    run.accept(last);

    while (iter.hasNext()) {
      var now = iter.next();
      separator.accept(last, now);
      run.accept(now);
      last = now;
    }
  }

  static <T> void forEach(@NotNull Iterable<T> it, @NotNull Runnable separator, @NotNull Consumer<T> run) {
    forEach(it, (_, _) -> separator.run(), run);
  }

  @Contract(pure = true)
  static <T> @NotNull SeqView<T> of(@NotNull Iterable<T> iter) {
    return iter::iterator;
  }

  @Contract(value = "_, _ -> new", pure = true)
  static <T> @NotNull Iterable<T> generator(@NotNull T init, Function<@NotNull T, @Nullable T> iterator) {
    final class Iter implements Iterator<T> {
      private T parent = null;    // only null at first iteration
      private T next = init;

      @Override
      public boolean hasNext() {
        if (next != null) return true;
        // multiple call on same parent is possible if iterator.apply(parent) == null
        next = iterator.apply(parent);
        return next != null;
      }

      @Override
      public T next() {
        if (!hasNext()) throw new ArrayIndexOutOfBoundsException("null");
        // hasNext() == true implies next != null
        var result = next;
        // consume next
        parent = next;
        next = null;

        return result;
      }
    }

    return new Iterable<>() {
      @Override
      public @NotNull Iterator<T> iterator() {
        return new Iter();
      }
    };
  }
}
