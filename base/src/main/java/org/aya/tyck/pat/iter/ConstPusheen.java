// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.pat.iter;

import org.aya.util.error.Panic;
import org.jetbrains.annotations.NotNull;

public record ConstPusheen<T, R>(@NotNull R body) implements Pusheenable<T, R> {
  @Override public @NotNull T peek() { return Panic.unreachable(); }
  @Override public @NotNull R body() { return body; }
  @Override public boolean hasNext() { return false; }
  @Override public T next() { return Panic.unreachable(); }
}
