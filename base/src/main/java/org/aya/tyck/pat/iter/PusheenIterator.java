// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.pat.iter;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Objects;

public class PusheenIterator<T, R> implements Pusheenable<T, @Nullable Pusheenable<T, R>> {
  protected final Iterator<T> iter;
  protected @Nullable T peek;
  protected final @Nullable Pusheenable<T, R> cat;
  protected boolean fromPusheen = false;

  public PusheenIterator(Iterator<T> iter, @Nullable Pusheenable<T, R> cat) {
    this.iter = iter;
    this.cat = cat;
  }

  @Override
  public boolean hasNext() {
    return peek != null || iter.hasNext() || (cat != null && cat.hasNext());
  }

  @Override
  public @NotNull T peek() {
    if (peek != null) return peek;
    if (iter.hasNext()) return peek = iter.next();

    var realPeek = postDoPeek(Objects.requireNonNull(cat).peek());
    peek = realPeek;
    fromPusheen = true;
    return realPeek;
  }

  protected @NotNull T postDoPeek(@NotNull T peeked) {
    return peeked;
  }

  @Override
  public T next() {
    if (peek == null) peek();

    if (fromPusheen) {
      // consume pusheen
      assert cat != null;
      cat.next();
    }

    var result = peek;
    peek = null;
    return result;
  }

  @Override
  public @Nullable Pusheenable<T, R> body() {
    return cat;
  }

  ///  Whether the last element comes from {@link #cat}
  public boolean isFromPusheen() {
    return fromPusheen;
  }
}
