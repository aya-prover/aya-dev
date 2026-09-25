// Copyright (c) 2020-2026 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.xtt;

import org.aya.syntax.ref.LocalVar;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public interface OrVar<T> {
  record Conc<T>(T value) implements OrVar<T> {
    @Override public @NotNull Conc<T> map(@NotNull Function<T, T> mapper) {
      var applied = mapper.apply(value);
      if (applied == value) return this;
      return new Conc<>(applied);
    }
  }
  record IsVar<T>(LocalVar var) implements OrVar<T> {
    @Override public @NotNull IsVar<T> map(@NotNull Function<T, T> mapper) {
      return this;
    }
  }

  @NotNull OrVar<T> map(@NotNull Function<T, T> mapper);
}
