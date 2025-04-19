// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.anf.frontend;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface Scope<V> {
  boolean hasName(String name);
  @Nullable V resolve(String name);

  record LetBindScope<V>(
    @NotNull String bind
  ) implements Scope<V> {

    @Override
    public boolean hasName(String name) {
      return bind.equals(name);
    }
    @Override
    public @Nullable V resolve(String name) {
      return null;
    }
  }
}
