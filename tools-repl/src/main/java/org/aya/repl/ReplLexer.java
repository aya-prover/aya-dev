// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.repl;

import kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;

public interface ReplLexer<T> {
  void reset(@NotNull CharSequence buf, int start, int end, int initialState);

  default void reset(@NotNull CharSequence buf, int initialState) {
    reset(buf, 0, buf.length(), initialState);
  }

  @NotNull ImmutableSeq<T> allTheWayDown();

  boolean isWhitespace(@NotNull T t);
  int startOffset(@NotNull T t);
  boolean containsOffset(@NotNull T t, int offset);
  @NotNull String tokenText(@NotNull String where, @NotNull T t);
}
